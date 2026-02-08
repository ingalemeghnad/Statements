package com.bank.mt.parsing;

import com.bank.mt.domain.MtStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw SWIFT MT messages (MT940/941/942/950) into MtStatement objects.
 * Extracts header fields and key tags from Block 4.
 */
@Component
public class MtParser {

    private static final Logger log = LoggerFactory.getLogger(MtParser.class);

    // Block 1: sender BIC is positions 4–11 (8 chars) after F01
    private static final Pattern BLOCK1_PATTERN = Pattern.compile("\\{1:F\\d{2}([A-Z0-9]{8,11})[A-Z0-9]*\\}");

    // Block 2: message type is 3 digits after I or O, receiver BIC follows
    private static final Pattern BLOCK2_INPUT_PATTERN = Pattern.compile("\\{2:I(\\d{3})([A-Z0-9]{8,12})\\w*\\}");
    private static final Pattern BLOCK2_OUTPUT_PATTERN = Pattern.compile("\\{2:O(\\d{3})\\d{4}([A-Z0-9]{8,12})");

    // Tag patterns in Block 4
    private static final Pattern TAG_20 = Pattern.compile(":20:(.+)");
    private static final Pattern TAG_25 = Pattern.compile(":25:(.+)");
    private static final Pattern TAG_28C = Pattern.compile(":28C:(\\d+)/(\\d+)");

    public MtStatement parse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new MtParseException("Raw message is null or blank");
        }

        MtStatement stmt = new MtStatement();
        stmt.setRawMessage(rawMessage);

        parseSenderBic(rawMessage, stmt);
        parseBlock2(rawMessage, stmt);
        parseBlock4Tags(rawMessage, stmt);

        log.debug("Parsed MT{} ref={} acct={} page={}/{}",
                stmt.getMessageType(), stmt.getTransactionReference(),
                stmt.getAccountNumber(), stmt.getPageNumber(), stmt.getTotalPages());

        return stmt;
    }

    private void parseSenderBic(String raw, MtStatement stmt) {
        Matcher m = BLOCK1_PATTERN.matcher(raw);
        if (m.find()) {
            stmt.setSenderBic(normalizeBic(m.group(1)));
        }
    }

    private void parseBlock2(String raw, MtStatement stmt) {
        Matcher input = BLOCK2_INPUT_PATTERN.matcher(raw);
        if (input.find()) {
            stmt.setMessageType("MT" + input.group(1));
            setReceiverBicWithBranch(stmt, input.group(2));
            return;
        }
        Matcher output = BLOCK2_OUTPUT_PATTERN.matcher(raw);
        if (output.find()) {
            stmt.setMessageType("MT" + output.group(1));
            setReceiverBicWithBranch(stmt, output.group(2));
        }
    }

    private void setReceiverBicWithBranch(MtStatement stmt, String fullBic) {
        stmt.setReceiverBic(normalizeBic(fullBic));
        if (fullBic != null && fullBic.length() > 8) {
            stmt.setReceiverBicBranch(fullBic.substring(8));
        }
    }

    private void parseBlock4Tags(String raw, MtStatement stmt) {
        Matcher ref = TAG_20.matcher(raw);
        if (ref.find()) {
            stmt.setTransactionReference(ref.group(1).trim());
        }

        Matcher acct = TAG_25.matcher(raw);
        if (acct.find()) {
            stmt.setAccountNumber(acct.group(1).trim());
        }

        Matcher page = TAG_28C.matcher(raw);
        if (page.find()) {
            stmt.setStatementNumber(page.group(1));
            int pageIndicator = Integer.parseInt(page.group(2));
            // In MT format :28C:SSSSS/PPPPP — the second number can mean page or total.
            // We parse the full set: look for all :28C: to determine multi-page.
            // Convention used here: :28C:statementNum/totalPages for first occurrence,
            // but we need to derive page number from context.
            parsePageInfo(raw, stmt, page.group(1), pageIndicator);
        } else {
            stmt.setPageNumber(1);
            stmt.setTotalPages(1);
        }
    }

    /**
     * Parse page information from :28C: tag.
     * Format: :28C:statementNumber/sequenceNumber
     *
     * Balance tag patterns determine page position:
     *   :60F: + :62F: = single page (first opening + final closing)
     *   :60F: + :62M: = first page of multi-page
     *   :60M: + :62M: = middle page of multi-page
     *   :60M: + :62F: = last page of multi-page (totalPages = this page number)
     *
     * For intermediate pages where totalPages is unknown, we set totalPages = 0.
     * The aggregation module resolves the total when the last page arrives.
     */
    private void parsePageInfo(String raw, MtStatement stmt, String stmtNum, int pageIndicator) {
        stmt.setPageNumber(pageIndicator);

        boolean hasFirstOpening = raw.contains(":60F:");
        boolean hasIntermediateOpening = raw.contains(":60M:");
        boolean hasFinalClosing = raw.contains(":62F:");
        boolean hasIntermediateClosing = raw.contains(":62M:");

        if (hasFirstOpening && hasFinalClosing && !hasIntermediateOpening && !hasIntermediateClosing) {
            // Single-page statement: first opening + final closing, no intermediate tags
            stmt.setTotalPages(pageIndicator);
        } else if (hasIntermediateOpening && hasFinalClosing) {
            // Last page of multi-page: we now know totalPages = this page number
            stmt.setTotalPages(pageIndicator);
        } else if (hasIntermediateOpening || hasIntermediateClosing) {
            // First or middle page of multi-page — total unknown
            stmt.setTotalPages(0);
        } else {
            stmt.setTotalPages(pageIndicator);
        }
    }

    private String normalizeBic(String bic) {
        // Return 8-char BIC for matching
        return bic != null && bic.length() >= 8 ? bic.substring(0, 8) : bic;
    }
}
