package com.bank.mt.aggregation;

import com.bank.mt.domain.*;
import com.bank.mt.repository.MtAggregationPageRepository;
import com.bank.mt.repository.MtAggregationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles multi-page statement aggregation.
 * Single-page statements pass through immediately.
 * Multi-page statements are collected until all pages arrive or the 1-hour window expires.
 */
@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);

    private final MtAggregationRepository aggregationRepo;
    private final MtAggregationPageRepository pageRepo;
    private final Counter completedCounter;
    private final Counter rejectedCounter;

    public AggregationService(MtAggregationRepository aggregationRepo,
                               MtAggregationPageRepository pageRepo,
                               MeterRegistry meterRegistry) {
        this.aggregationRepo = aggregationRepo;
        this.pageRepo = pageRepo;
        this.completedCounter = meterRegistry.counter("mt.aggregation.completed");
        this.rejectedCounter = meterRegistry.counter("mt.aggregation.rejected");
    }

    @Transactional
    public AggregationResult aggregate(MtStatement statement, Long odsMessageId) {
        // Single-page statements bypass aggregation (totalPages=0 means unknown, NOT single)
        if (statement.getTotalPages() == 1 && statement.getPageNumber() == 1) {
            log.info("Single-page {} ref={} — skipping aggregation",
                    statement.getMessageType(), statement.getTransactionReference());
            completedCounter.increment();
            return AggregationResult.ready(statement, List.of(odsMessageId));
        }

        String checksum = computeChecksum(statement.getRawMessage());
        String transactionReference = normalizeReference(statement.getTransactionReference());

        // Find or create aggregation group
        Optional<MtAggregation> existing = aggregationRepo
                .findByStatementNumberAndAccountNumberAndMessageTypeAndTransactionReference(
                        statement.getStatementNumber(),
                        statement.getAccountNumber(),
                        statement.getMessageType(),
                        transactionReference);

        MtAggregation agg;
        if (existing.isPresent()) {
            agg = existing.get();

            // Already completed or rejected — skip
            if (agg.getStatus() != AggregationStatus.IN_PROGRESS) {
                log.warn("Aggregation {} already in status {}", agg.getId(), agg.getStatus());
                return AggregationResult.rejected();
            }

            // Duplicate page detection via checksum
            if (pageRepo.existsByAggregationIdAndChecksum(agg.getId(), checksum)) {
                log.warn("Duplicate page detected for aggregation {} checksum={}", agg.getId(), checksum);
                rejectedCounter.increment();
                return AggregationResult.rejected();
            }
        } else {
            agg = new MtAggregation();
            agg.setStatementNumber(statement.getStatementNumber());
            agg.setAccountNumber(statement.getAccountNumber());
            agg.setMessageType(statement.getMessageType());
            agg.setTransactionReference(transactionReference);
            agg.setStatus(AggregationStatus.IN_PROGRESS);
            // Total pages: use from statement if known, otherwise estimate from page number
            int totalPages = statement.getTotalPages() > 0 ? statement.getTotalPages() : 0;
            agg.setTotalPages(totalPages);
            agg.setReceivedPages(0);
            agg = aggregationRepo.save(agg);
        }

        // Add the page
        MtAggregationPage page = new MtAggregationPage();
        page.setPageNumber(statement.getPageNumber());
        page.setRawMessage(statement.getRawMessage());
        page.setChecksum(checksum);
        page.setOdsMessageId(odsMessageId);
        agg.addPage(page);
        agg.setReceivedPages(agg.getReceivedPages() + 1);

        // Update total pages if we learn it from a later page
        if (statement.getTotalPages() > agg.getTotalPages()) {
            agg.setTotalPages(statement.getTotalPages());
        }

        // Check if all pages received
        if (agg.getTotalPages() > 0 && agg.getReceivedPages() >= agg.getTotalPages()) {
            agg.setStatus(AggregationStatus.COMPLETED);
            aggregationRepo.save(agg);

            MtStatement combined = buildCombinedStatement(agg, statement);
            log.info("Aggregation complete for stmt={} acct={} ({} pages)",
                    agg.getStatementNumber(), agg.getAccountNumber(), agg.getTotalPages());
            completedCounter.increment();
            List<Long> relatedOdsIds = agg.getPages().stream()
                    .map(MtAggregationPage::getOdsMessageId)
                    .filter(id -> id != null)
                    .distinct()
                    .toList();
            return AggregationResult.ready(combined, relatedOdsIds);
        }

        aggregationRepo.save(agg);
        log.info("Aggregation in progress for stmt={} acct={} ({}/{} pages)",
                agg.getStatementNumber(), agg.getAccountNumber(),
                agg.getReceivedPages(), agg.getTotalPages());
        return AggregationResult.pending();
    }

    private String normalizeReference(String reference) {
        return reference == null ? "" : reference.trim();
    }

    private MtStatement buildCombinedStatement(MtAggregation agg, MtStatement lastPage) {
        MtStatement combined = new MtStatement();
        combined.setMessageType(agg.getMessageType());
        combined.setAccountNumber(agg.getAccountNumber());
        combined.setStatementNumber(agg.getStatementNumber());
        combined.setPageNumber(1);
        combined.setTotalPages(1); // combined = single logical statement
        combined.setSenderBic(lastPage.getSenderBic());
        combined.setReceiverBic(lastPage.getReceiverBic());
        combined.setTransactionReference(lastPage.getTransactionReference());

        List<MtAggregationPage> sortedPages = agg.getPages().stream()
                .sorted(Comparator.comparingInt(MtAggregationPage::getPageNumber))
                .toList();

        combined.setRawMessage(buildCombinedRawMessage(sortedPages));
        return combined;
    }

    /**
     * Builds a single combined SWIFT FIN message from multiple pages.
     *
     * Uses the header (Block 1 + Block 2) from the first page,
     * takes header tags (:20:, :25:, :28C:) and opening balance (:60F:) from page 1,
     * merges transaction lines (:61:, :86:) from all pages in order,
     * and takes the final closing balance (:62F:) and summary tags from the last page.
     *
     * Intermediate balance tags (:60M:, :62M:) are discarded.
     */
    private String buildCombinedRawMessage(List<MtAggregationPage> sortedPages) {
        String firstPageRaw = sortedPages.get(0).getRawMessage();

        // Extract SWIFT header (Block 1 + Block 2) from first page
        String header = extractSwiftHeader(firstPageRaw);

        // Merge Block 4 body from all pages
        List<String> combinedBody = new ArrayList<>();
        for (int i = 0; i < sortedPages.size(); i++) {
            List<String> bodyLines = extractBlock4Lines(sortedPages.get(i).getRawMessage());
            boolean isFirst = (i == 0);
            boolean isLast = (i == sortedPages.size() - 1);

            for (String line : bodyLines) {
                String tag = extractTag(line);
                if (tag == null) continue;

                // Skip intermediate balance tags — not part of combined output
                if ("60M".equals(tag) || "62M".equals(tag)) continue;

                // Header + opening balance tags: only from first page
                if (HEADER_TAGS.contains(tag)) {
                    if (isFirst) combinedBody.add(line);
                    continue;
                }

                // Closing balance + summary tags: only from last page
                if (CLOSING_TAGS.contains(tag)) {
                    if (isLast) combinedBody.add(line);
                    continue;
                }

                // Transaction lines (:61:, :86:, etc.): from all pages
                combinedBody.add(line);
            }
        }

        // Assemble: header + Block 4
        StringBuilder sb = new StringBuilder();
        sb.append(header).append("{4:\n");
        for (String line : combinedBody) {
            sb.append(line).append("\n");
        }
        sb.append("-}");
        return sb.toString();
    }

    // Tags from first page only: header fields + opening balance
    private static final Set<String> HEADER_TAGS = Set.of(
            "20", "21", "25", "28C", "34F", "13D", "60F");

    // Tags from last page only: closing balance + summary
    private static final Set<String> CLOSING_TAGS = Set.of(
            "62F", "64", "65", "90D", "90C");

    private static final Pattern SWIFT_HEADER_PATTERN =
            Pattern.compile("(\\{1:[^}]+\\}\\{2:[^}]+\\})");

    private static final Pattern BLOCK4_BODY_PATTERN =
            Pattern.compile("\\{4:\\s*\\n?(.*?)\\n?-\\}", Pattern.DOTALL);

    private String extractSwiftHeader(String raw) {
        Matcher m = SWIFT_HEADER_PATTERN.matcher(raw);
        return m.find() ? m.group(1) : "";
    }

    private List<String> extractBlock4Lines(String raw) {
        Matcher m = BLOCK4_BODY_PATTERN.matcher(raw);
        if (!m.find()) return List.of();
        return Arrays.stream(m.group(1).split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String extractTag(String line) {
        if (!line.startsWith(":")) return null;
        int endColon = line.indexOf(':', 1);
        if (endColon <= 1) return null;
        return line.substring(1, endColon);
    }

    private String computeChecksum(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
