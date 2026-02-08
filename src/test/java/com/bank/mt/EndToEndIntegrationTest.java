package com.bank.mt;

import com.bank.mt.delivery.MockDeliveryAdapter;
import com.bank.mt.domain.*;
import com.bank.mt.ingestion.PollingOdsIngestionStrategy;
import com.bank.mt.repository.*;
import com.bank.mt.routing.RoutingService;
import com.bank.mt.ruleloader.RuleLoaderService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndIntegrationTest {

    @Autowired private MtMessageOdsRepository odsRepo;
    @Autowired private RoutingRuleRepository ruleRepo;
    @Autowired private RelayConfigRepository relayRepo;
    @Autowired private PollingOdsIngestionStrategy ingestion;
    @Autowired private MockDeliveryAdapter mockAdapter;
    @Autowired private RoutingService routingService;
    @Autowired private RuleLoaderService ruleLoaderService;

    @BeforeEach
    void setUp() {
        mockAdapter.clear();
    }

    @Test
    @Order(1)
    void singlePageMt940_endToEnd() throws Exception {
        // Insert a fresh single-page MT940
        MtMessageOds ods = new MtMessageOds();
        ods.setRawMessage("""
                {1:F01BANKGB22AXXX0000000000}{2:I940CLIENTBICXXXXN}{4:
                :20:E2EREF01
                :25:123456789
                :28C:00001/001
                :60F:C210101EUR1000,
                :61:2101010101DR100,
                :62F:C210101EUR900,
                -}""");
        ods.setStatus(OdsStatus.NEW);
        odsRepo.save(ods);

        // Ensure routing rule and relay config exist (from V2 migration)
        routingService.refreshCache();

        // Trigger polling
        ingestion.start();
        Thread.sleep(500);

        List<DeliveryRecord> deliveries = mockAdapter.getDeliveries();

        boolean hasReportingQ1 = deliveries.stream()
                .anyMatch(d -> "REPORTING.Q1".equals(d.getDestination())
                        && "123456789".equals(d.getAccountNumber()));
        boolean hasSwiftRelay = deliveries.stream()
                .anyMatch(d -> "SWIFT.RELAY".equals(d.getDestination())
                        && "123456789".equals(d.getAccountNumber()));

        assertTrue(hasReportingQ1, "Expected delivery to REPORTING.Q1, got: " + deliveries.stream()
                .map(d -> d.getDestination() + "/" + d.getAccountNumber()).toList());
        assertTrue(hasSwiftRelay, "Expected relay to SWIFT.RELAY");
    }

    @Test
    @Order(2)
    void multiPageMt940_aggregationAndDelivery() throws Exception {
        // Insert page 1 of 2 (with :60F: opening balance, no :62F: final)
        MtMessageOds page1 = new MtMessageOds();
        page1.setRawMessage("""
                {1:F01BANKGB22AXXX0000000000}{2:I940CLIENTBICXXXXN}{4:
                :20:MPREF01
                :25:123456789
                :28C:00010/001
                :60F:C210201EUR5000,
                :61:2102010201DR200,
                :62M:C210201EUR4800,
                -}""");
        page1.setStatus(OdsStatus.NEW);
        odsRepo.save(page1);

        // Poll page 1 — should aggregate (pending)
        ingestion.start();
        Thread.sleep(300);

        // Insert page 2 of 2 (with :62F: final balance)
        MtMessageOds page2 = new MtMessageOds();
        page2.setRawMessage("""
                {1:F01BANKGB22AXXX0000000000}{2:I940CLIENTBICXXXXN}{4:
                :20:MPREF01
                :25:123456789
                :28C:00010/002
                :60M:C210201EUR4800,
                :61:2102010201CR1000,
                :62F:C210201EUR5800,
                -}""");
        page2.setStatus(OdsStatus.NEW);
        odsRepo.save(page2);

        routingService.refreshCache();

        // Poll page 2 — should complete aggregation and route
        ingestion.start();
        Thread.sleep(500);

        List<DeliveryRecord> deliveries = mockAdapter.getDeliveries();
        assertFalse(deliveries.isEmpty(), "Expected deliveries after multi-page aggregation");

        boolean hasReporting = deliveries.stream()
                .anyMatch(d -> "REPORTING.Q1".equals(d.getDestination()));
        assertTrue(hasReporting, "Expected delivery to REPORTING.Q1 for multi-page MT940");
    }

    @Test
    @Order(3)
    void mt942_routesCorrectly() throws Exception {
        // Add routing rule for MT942
        RoutingRule rule = new RoutingRule();
        rule.setAccountNumber("987654321");
        rule.setMessageType("MT942");
        rule.setSenderBic("BANKGB22");
        rule.setReceiverBic("CLIENTBI");
        rule.setDestinationQueue("INTRADAY.Q1");
        rule.setActive(true);
        rule.setSource(RuleSource.UI);
        ruleRepo.save(rule);
        routingService.refreshCache();

        // Insert MT942 message
        MtMessageOds ods = new MtMessageOds();
        ods.setRawMessage("""
                {1:F01BANKGB22AXXX0000000000}{2:I942CLIENTBICXXXXN}{4:
                :20:MT942REF
                :25:987654321
                :28C:00001/001
                :34F:EUR100,
                :13D:2101011200+0100
                :61:2101010101CR500,
                :90D:1EUR100,
                :90C:1EUR500,
                -}""");
        ods.setStatus(OdsStatus.NEW);
        odsRepo.save(ods);

        ingestion.start();
        Thread.sleep(500);

        List<DeliveryRecord> deliveries = mockAdapter.getDeliveries();
        boolean hasIntradayQ1 = deliveries.stream()
                .anyMatch(d -> "INTRADAY.Q1".equals(d.getDestination()));
        assertTrue(hasIntradayQ1, "Expected delivery to INTRADAY.Q1 for MT942, got: " +
                deliveries.stream().map(DeliveryRecord::getDestination).toList());
    }

    @Test
    @Order(4)
    void crudRuleUpdate_refreshesCache() {
        long initialCount = ruleRepo.count();

        RoutingRule rule = new RoutingRule();
        rule.setAccountNumber("TESTACCT");
        rule.setMessageType("MT950");
        rule.setDestinationQueue("TEST.Q1");
        rule.setActive(true);
        rule.setSource(RuleSource.UI);
        RoutingRule saved = ruleRepo.save(rule);
        routingService.refreshCache();

        assertEquals(initialCount + 1, ruleRepo.count());

        // Update
        saved.setDestinationQueue("TEST.Q2");
        ruleRepo.save(saved);
        routingService.refreshCache();

        RoutingRule updated = ruleRepo.findById(saved.getId()).orElseThrow();
        assertEquals("TEST.Q2", updated.getDestinationQueue());

        // Delete
        ruleRepo.deleteById(saved.getId());
        routingService.refreshCache();
        assertEquals(initialCount, ruleRepo.count());
    }

    @Test
    @Order(5)
    void fileRuleLoad_replacesOnlyFileRules() {
        // Insert a UI rule that should survive file reload
        RoutingRule uiRule = new RoutingRule();
        uiRule.setAccountNumber("UI_ACCT");
        uiRule.setMessageType("MT940");
        uiRule.setDestinationQueue("UI.Q1");
        uiRule.setActive(true);
        uiRule.setSource(RuleSource.UI);
        ruleRepo.save(uiRule);

        int loaded = ruleLoaderService.loadRulesFromFile();
        assertTrue(loaded > 0, "Should have loaded rules from CSV");

        // UI rule should still exist
        boolean uiRuleExists = ruleRepo.findAll().stream()
                .anyMatch(r -> "UI_ACCT".equals(r.getAccountNumber()) && r.getSource() == RuleSource.UI);
        assertTrue(uiRuleExists, "UI rule should survive file reload");

        // FILE rules should exist
        boolean fileRulesExist = ruleRepo.findAll().stream()
                .anyMatch(r -> r.getSource() == RuleSource.FILE);
        assertTrue(fileRulesExist, "FILE rules should be loaded");
    }
}
