package com.bank.mt.delivery;

import com.bank.mt.domain.DeliveryInstruction;
import com.bank.mt.domain.MtStatement;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates delivery to downstream destinations and SWIFT relay.
 * For SWIFT relay, replaces the receiver BIC in Block 2 of the raw message
 * with the configured swift_receiver_bic before delivering.
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final String SWIFT_RELAY_DESTINATION = "SWIFT.ALLIANCE.OUTBOUND";

    // Block 2 input: {2:I<type:3><receiverBic:8><branch:4><priority:1>}
    private static final Pattern BLOCK2_BIC_PATTERN =
            Pattern.compile("(\\{2:I\\d{3})[A-Z0-9]{8}");

    private final DeliveryAdapter adapter;
    private final Counter successCounter;
    private final Counter failureCounter;

    @Value("${mt.delivery.retry-max-attempts:3}")
    private int maxRetries;

    public DeliveryService(DeliveryAdapter adapter, MeterRegistry meterRegistry) {
        this.adapter = adapter;
        this.successCounter = meterRegistry.counter("mt.delivery.success");
        this.failureCounter = meterRegistry.counter("mt.delivery.failure");
    }

    public boolean deliver(DeliveryInstruction instruction) {
        boolean allDelivered = true;

        // Deliver to each downstream destination (original message)
        for (String destination : instruction.getDownstreamDestinations()) {
            allDelivered = deliverWithRetry(destination, instruction.getStatement()) && allDelivered;
        }

        // Relay to SWIFT with receiver BIC replacement
        if (instruction.isRelayToSwift()) {
            MtStatement relayStatement = buildRelayStatement(
                    instruction.getStatement(), instruction.getSwiftReceiverBic());
            allDelivered = deliverWithRetry(SWIFT_RELAY_DESTINATION, relayStatement) && allDelivered;
        }

        return allDelivered;
    }

    /**
     * Creates a copy of the statement with the Block 2 receiver BIC replaced
     * for SWIFT relay delivery.
     */
    private MtStatement buildRelayStatement(MtStatement original, String swiftReceiverBic) {
        MtStatement relay = new MtStatement();
        relay.setMessageType(original.getMessageType());
        relay.setAccountNumber(original.getAccountNumber());
        relay.setStatementNumber(original.getStatementNumber());
        relay.setPageNumber(original.getPageNumber());
        relay.setTotalPages(original.getTotalPages());
        relay.setSenderBic(original.getSenderBic());
        relay.setReceiverBic(swiftReceiverBic);
        relay.setTransactionReference(original.getTransactionReference());
        relay.setRawMessage(replaceReceiverBic(original.getRawMessage(), swiftReceiverBic));

        log.info("SWIFT relay: replaced receiver BIC {} → {} for ref={}",
                original.getReceiverBic(), swiftReceiverBic, original.getTransactionReference());
        return relay;
    }

    /**
     * Replaces the 8-char receiver BIC in Block 2 of the raw SWIFT message.
     * Block 2 format: {2:I<type:3><receiverBic:8><branch+priority>}
     */
    private String replaceReceiverBic(String rawMessage, String newReceiverBic) {
        Matcher m = BLOCK2_BIC_PATTERN.matcher(rawMessage);
        if (m.find()) {
            return m.replaceFirst("$1" + newReceiverBic);
        }
        log.warn("Could not find Block 2 receiver BIC to replace in raw message");
        return rawMessage;
    }

    private boolean deliverWithRetry(String destination, MtStatement statement) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                adapter.deliver(destination, statement);
                successCounter.increment();
                return true;
            } catch (Exception e) {
                log.warn("Delivery attempt {}/{} failed for dest={} ref={}: {}",
                        attempt, maxRetries, destination,
                        statement.getTransactionReference(),
                        e.getMessage());
                if (attempt == maxRetries) {
                    log.error("Delivery exhausted retries for dest={} ref={}",
                            destination, statement.getTransactionReference());
                    failureCounter.increment();
                }
            }
        }

        return false;
    }
}
