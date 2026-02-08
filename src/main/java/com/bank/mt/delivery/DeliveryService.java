package com.bank.mt.delivery;

import com.bank.mt.domain.DeliveryInstruction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Orchestrates delivery to downstream destinations and SWIFT relay.
 * Async execution with retry support.
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

    @Async("deliveryExecutor")
    public void deliver(DeliveryInstruction instruction) {
        // Deliver to each downstream destination
        for (String destination : instruction.getDownstreamDestinations()) {
            deliverWithRetry(destination, instruction);
        }

        // Relay to SWIFT if configured
        if (instruction.isRelayToSwift()) {
            deliverWithRetry(SWIFT_RELAY_DESTINATION, instruction);
        }
    }

    private void deliverWithRetry(String destination, DeliveryInstruction instruction) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                adapter.deliver(destination, instruction.getStatement());
                successCounter.increment();
                return;
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
    }
}
