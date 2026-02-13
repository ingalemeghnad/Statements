package com.bank.mt.ingestion;

import com.bank.mt.aggregation.AggregationService;
import com.bank.mt.domain.AggregationResult;
import com.bank.mt.domain.MtMessageOds;
import com.bank.mt.domain.MtStatement;
import com.bank.mt.domain.OdsStatus;
import com.bank.mt.parsing.MtParser;
import com.bank.mt.repository.MtMessageOdsRepository;
import com.bank.mt.routing.RoutingService;
import com.bank.mt.domain.DeliveryInstruction;
import com.bank.mt.delivery.DeliveryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

/**
 * MQ-based ingestion strategy — receives raw MT messages from an inbound queue.
 *
 * In production, wire this with @JmsListener or a platform-specific MQ listener
 * (e.g., IBM MQ, RabbitMQ, ActiveMQ) pointing to the configured inbound queue.
 *
 * For POC purposes, the REST controller invokes onMessage() directly to simulate
 * MQ delivery without requiring a running broker.
 */
@Component
@ConditionalOnProperty(name = "mt.ingestion.mode", havingValue = "MQ")
public class MqIngestionStrategy implements MtIngestionStrategy {

    private static final Logger log = LoggerFactory.getLogger(MqIngestionStrategy.class);

    private final MtMessageOdsRepository odsRepository;
    private final MtParser parser;
    private final AggregationService aggregationService;
    private final RoutingService routingService;
    private final DeliveryService deliveryService;
    private final Counter processedCounter;

    public MqIngestionStrategy(MtMessageOdsRepository odsRepository,
                               MtParser parser,
                               AggregationService aggregationService,
                               RoutingService routingService,
                               DeliveryService deliveryService,
                               MeterRegistry meterRegistry) {
        this.odsRepository = odsRepository;
        this.parser = parser;
        this.aggregationService = aggregationService;
        this.routingService = routingService;
        this.deliveryService = deliveryService;
        this.processedCounter = meterRegistry.counter("mt.ingestion.processed");
    }

    @Override
    public void start() {
        log.info("MQ ingestion strategy active — listening on inbound queue");
    }

    /**
     * Processes a raw MT message received from the MQ inbound queue.
     * Saves to ODS for audit, then routes through the pipeline.
     */
    public void onMessage(String rawMessage) {
        log.info("Received message from MQ inbound queue ({} chars)", rawMessage.length());

        // Persist to ODS for audit trail
        MtMessageOds ods = new MtMessageOds();
        ods.setRawMessage(rawMessage);
        ods.setStatus(OdsStatus.PROCESSING);
        ods = odsRepository.save(ods);

        processMessage(ods);
    }

    private void processMessage(MtMessageOds ods) {
        try {
            MtStatement statement = parser.parse(ods.getRawMessage());

            AggregationResult result = aggregationService.aggregate(statement, ods.getId());

            if (result.isRejected()) {
                markFailed(ods, "Aggregation rejected (duplicate page)");
                return;
            }

            if (result.isReadyForRouting()) {
                List<Long> relatedOdsIds = Stream.concat(
                                result.getRelatedOdsMessageIds().stream(),
                                Stream.of(ods.getId()))
                        .distinct()
                        .toList();

                if (routeAndDeliver(result.getCombinedStatement())) {
                    markCompletedByIds(relatedOdsIds);
                } else {
                    markFailedByIds(relatedOdsIds, "Delivery failed after retries");
                }
            }
            // else: still waiting for more pages — leave as PROCESSING

            processedCounter.increment();
        } catch (Exception e) {
            log.error("Failed to process MQ message odsId={}", ods.getId(), e);
            markFailed(ods, e.getMessage());
        }
    }

    private boolean routeAndDeliver(MtStatement statement) {
        DeliveryInstruction instruction = routingService.route(statement);
        return deliveryService.deliver(instruction);
    }

    private void markCompleted(MtMessageOds ods) {
        ods.setStatus(OdsStatus.COMPLETED);
        odsRepository.save(ods);
    }

    private void markCompletedByIds(List<Long> odsIds) {
        for (Long odsId : odsIds) {
            odsRepository.findById(odsId).ifPresent(this::markCompleted);
        }
    }

    private void markFailed(MtMessageOds ods, String reason) {
        ods.setStatus(OdsStatus.FAILED);
        ods.setErrorReason(reason);
        ods.setRetryCount(ods.getRetryCount() + 1);
        odsRepository.save(ods);
    }

    private void markFailedByIds(List<Long> odsIds, String reason) {
        for (Long odsId : odsIds) {
            odsRepository.findById(odsId).ifPresent(o -> markFailed(o, reason));
        }
    }
}
