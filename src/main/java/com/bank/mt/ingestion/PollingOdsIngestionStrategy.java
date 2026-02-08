package com.bank.mt.ingestion;

import com.bank.mt.aggregation.AggregationFilter;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@ConditionalOnProperty(name = "mt.ingestion.mode", havingValue = "POLLING", matchIfMissing = true)
public class PollingOdsIngestionStrategy implements MtIngestionStrategy {

    private static final Logger log = LoggerFactory.getLogger(PollingOdsIngestionStrategy.class);

    private final MtMessageOdsRepository odsRepository;
    private final MtParser parser;
    private final AggregationFilter aggregationFilter;
    private final AggregationService aggregationService;
    private final RoutingService routingService;
    private final DeliveryService deliveryService;
    private final Counter processedCounter;

    @Value("${mt.ingestion.polling.batch-size:200}")
    private int batchSize;

    public PollingOdsIngestionStrategy(MtMessageOdsRepository odsRepository,
                                       MtParser parser,
                                       AggregationFilter aggregationFilter,
                                       AggregationService aggregationService,
                                       RoutingService routingService,
                                       DeliveryService deliveryService,
                                       MeterRegistry meterRegistry) {
        this.odsRepository = odsRepository;
        this.parser = parser;
        this.aggregationFilter = aggregationFilter;
        this.aggregationService = aggregationService;
        this.routingService = routingService;
        this.deliveryService = deliveryService;
        this.processedCounter = meterRegistry.counter("mt.ingestion.processed");
    }

    @Override
    @Scheduled(fixedDelayString = "${mt.ingestion.polling.interval-ms:5000}")
    @Transactional
    public void start() {
        log.debug("Polling ODS for new messages (batch size: {})", batchSize);
        List<MtMessageOds> batch = odsRepository.findByStatusOrderByIdLimit(
                OdsStatus.NEW, PageRequest.of(0, batchSize));

        if (batch.isEmpty()) {
            return;
        }

        log.info("Picked up {} messages from ODS", batch.size());

        // Mark as PROCESSING
        List<Long> ids = batch.stream().map(MtMessageOds::getId).toList();
        odsRepository.updateStatusBatch(ids, OdsStatus.NEW, OdsStatus.PROCESSING);

        for (MtMessageOds ods : batch) {
            processMessage(ods);
        }
    }

    private void processMessage(MtMessageOds ods) {
        try {
            MtStatement statement = parser.parse(ods.getRawMessage());

            if (!aggregationFilter.shouldAggregate(statement)) {
                // Skip aggregation — route individual page directly
                log.info("Aggregation skipped for ref={} receiverBic={} — routing directly",
                        statement.getTransactionReference(), statement.getReceiverBic());
                routeAndDeliver(statement);
                markCompleted(ods);
                processedCounter.increment();
                return;
            }

            AggregationResult result = aggregationService.aggregate(statement, ods.getId());

            if (result.isRejected()) {
                markFailed(ods, "Aggregation rejected (duplicate page)");
                return;
            }

            if (result.isReadyForRouting()) {
                routeAndDeliver(result.getCombinedStatement());
                markCompleted(ods);
            }
            // else: still waiting for more pages — leave as PROCESSING

            processedCounter.increment();
        } catch (Exception e) {
            log.error("Failed to process ODS message id={}", ods.getId(), e);
            markFailed(ods, e.getMessage());
        }
    }

    private void routeAndDeliver(MtStatement statement) {
        DeliveryInstruction instruction = routingService.route(statement);
        deliveryService.deliver(instruction);
    }

    private void markCompleted(MtMessageOds ods) {
        ods.setStatus(OdsStatus.COMPLETED);
        odsRepository.save(ods);
    }

    private void markFailed(MtMessageOds ods, String reason) {
        ods.setStatus(OdsStatus.FAILED);
        ods.setErrorReason(reason);
        ods.setRetryCount(ods.getRetryCount() + 1);
        odsRepository.save(ods);
    }
}
