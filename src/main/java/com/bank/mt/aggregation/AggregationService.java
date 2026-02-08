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
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Collectors;

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
            return AggregationResult.ready(statement);
        }

        String checksum = computeChecksum(statement.getRawMessage());

        // Find or create aggregation group
        Optional<MtAggregation> existing = aggregationRepo
                .findByStatementNumberAndAccountNumberAndMessageType(
                        statement.getStatementNumber(),
                        statement.getAccountNumber(),
                        statement.getMessageType());

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
            return AggregationResult.ready(combined);
        }

        aggregationRepo.save(agg);
        log.info("Aggregation in progress for stmt={} acct={} ({}/{} pages)",
                agg.getStatementNumber(), agg.getAccountNumber(),
                agg.getReceivedPages(), agg.getTotalPages());
        return AggregationResult.pending();
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

        // Concatenate all page raw messages in page order
        String combinedRaw = agg.getPages().stream()
                .sorted((a, b) -> Integer.compare(a.getPageNumber(), b.getPageNumber()))
                .map(MtAggregationPage::getRawMessage)
                .collect(Collectors.joining("\n---PAGE-BREAK---\n"));
        combined.setRawMessage(combinedRaw);

        return combined;
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
