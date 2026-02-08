package com.bank.mt.delivery;

import com.bank.mt.domain.DeliveryInstruction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates delivery to downstream destinations and SWIFT relay.
 * Executes delivery with retry support and returns final success/failure.
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final String SWIFT_RELAY_DESTINATION = "SWIFT.RELAY";

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

        // Deliver to each downstream destination
        for (String destination : instruction.getDownstreamDestinations()) {
            allDelivered = deliverWithRetry(destination, instruction) && allDelivered;
        }

        // Relay to SWIFT if configured
        if (instruction.isRelayToSwift()) {
            allDelivered = deliverWithRetry(SWIFT_RELAY_DESTINATION, instruction) && allDelivered;
        }

        return allDelivered;
    }

    private boolean deliverWithRetry(String destination, DeliveryInstruction instruction) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                adapter.deliver(destination, instruction.getStatement());
                successCounter.increment();
                return true;
            } catch (Exception e) {
                log.warn("Delivery attempt {}/{} failed for dest={} ref={}: {}",
                        attempt, maxRetries, destination,
                        instruction.getStatement().getTransactionReference(),
                        e.getMessage());
                if (attempt == maxRetries) {
                    log.error("Delivery exhausted retries for dest={} ref={}",
                            destination, instruction.getStatement().getTransactionReference());
                    failureCounter.increment();
                }
            }
        }

        return false;
    }
}
