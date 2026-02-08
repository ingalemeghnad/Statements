package com.bank.mt;

import com.bank.mt.domain.*;
import com.bank.mt.repository.MtAggregationRepository;
import com.bank.mt.repository.MtMessageOdsRepository;
import com.bank.mt.scheduler.AggregationExpiryScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AggregationTimeoutTest {

    @Autowired
    private MtAggregationRepository aggRepo;

    @Autowired
    private MtMessageOdsRepository odsRepo;

    @Autowired
    private AggregationExpiryScheduler scheduler;

    @Test
    void expiredAggregation_isRejectedAndOdsMarkedFailed() {
        // Create an ODS message
        MtMessageOds ods = new MtMessageOds();
        ods.setRawMessage("test");
        ods.setStatus(OdsStatus.PROCESSING);
        ods = odsRepo.save(ods);

        // Create an aggregation that is already expired (created 2 hours ago)
        MtAggregation agg = new MtAggregation();
        agg.setStatementNumber("TIMEOUT_TEST");
        agg.setAccountNumber("TIMEOUT_ACCT");
        agg.setMessageType("MT940");
        agg.setTotalPages(3);
        agg.setReceivedPages(1);
        agg.setStatus(AggregationStatus.IN_PROGRESS);
        agg = aggRepo.save(agg);

        // Manually set created_at to 2 hours ago to trigger expiry
        agg.setCreatedAt(LocalDateTime.now().minusHours(2));
        agg = aggRepo.save(agg);

        // Add a page referencing the ODS message
        MtAggregationPage page = new MtAggregationPage();
        page.setPageNumber(1);
        page.setRawMessage("test page");
        page.setChecksum("abc123");
        page.setOdsMessageId(ods.getId());
        agg.addPage(page);
        aggRepo.save(agg);

        // Run the scheduler
        scheduler.expireStaleAggregations();

        // Verify aggregation is REJECTED
        MtAggregation updated = aggRepo.findById(agg.getId()).orElseThrow();
        assertEquals(AggregationStatus.REJECTED, updated.getStatus());

        // Verify ODS message is FAILED
        MtMessageOds updatedOds = odsRepo.findById(ods.getId()).orElseThrow();
        assertEquals(OdsStatus.FAILED, updatedOds.getStatus());
        assertNotNull(updatedOds.getErrorReason());
        assertTrue(updatedOds.getErrorReason().contains("timeout"));
    }
}
