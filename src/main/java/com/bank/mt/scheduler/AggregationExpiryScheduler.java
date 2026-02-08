package com.bank.mt.scheduler;

import com.bank.mt.domain.AggregationStatus;
import com.bank.mt.domain.MtAggregation;
import com.bank.mt.domain.MtAggregationPage;
import com.bank.mt.domain.OdsStatus;
import com.bank.mt.repository.MtAggregationRepository;
import com.bank.mt.repository.MtMessageOdsRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically checks for expired aggregations (exceeded 1-hour window).
 * Marks them REJECTED and updates associated ODS messages to FAILED.
 */
@Component
public class AggregationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AggregationExpiryScheduler.class);

    private final MtAggregationRepository aggregationRepo;
    private final MtMessageOdsRepository odsRepo;
    private final Counter rejectedCounter;

    @Value("${mt.aggregation.expiry-minutes:60}")
    private int expiryMinutes;

    public AggregationExpiryScheduler(MtAggregationRepository aggregationRepo,
                                       MtMessageOdsRepository odsRepo,
                                       MeterRegistry meterRegistry) {
        this.aggregationRepo = aggregationRepo;
        this.odsRepo = odsRepo;
        this.rejectedCounter = meterRegistry.counter("mt.aggregation.rejected");
    }

    @Scheduled(fixedDelayString = "${mt.aggregation.scheduler-interval-ms:60000}")
    @Transactional
    public void expireStaleAggregations() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(expiryMinutes);
        List<MtAggregation> expired = aggregationRepo
                .findExpiredAggregations(AggregationStatus.IN_PROGRESS, cutoff);

        if (expired.isEmpty()) {
            return;
        }

        log.info("Found {} expired aggregations (cutoff={})", expired.size(), cutoff);

        for (MtAggregation agg : expired) {
            agg.setStatus(AggregationStatus.REJECTED);
            aggregationRepo.save(agg);

            // Mark associated ODS messages as FAILED
            for (MtAggregationPage page : agg.getPages()) {
                if (page.getOdsMessageId() != null) {
                    odsRepo.findById(page.getOdsMessageId()).ifPresent(ods -> {
                        ods.setStatus(OdsStatus.FAILED);
                        ods.setErrorReason("Aggregation timeout — not all pages received within "
                                + expiryMinutes + " minutes");
                        odsRepo.save(ods);
                    });
                }
            }

            rejectedCounter.increment();
            log.warn("Rejected aggregation id={} stmt={} acct={} ({}/{} pages received)",
                    agg.getId(), agg.getStatementNumber(), agg.getAccountNumber(),
                    agg.getReceivedPages(), agg.getTotalPages());
        }
    }
}
