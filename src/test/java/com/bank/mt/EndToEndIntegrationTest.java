package com.bank.mt;

import com.bank.mt.delivery.MockDeliveryAdapter;
import com.bank.mt.domain.*;
import com.bank.mt.ingestion.MqIngestionStrategy;
import com.bank.mt.repository.*;
import com.bank.mt.routing.RoutingService;
import com.bank.mt.ruleloader.RuleLoaderService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndIntegrationTest {

    @Autowired private MtMessageOdsRepository odsRepo;
    @Autowired private RoutingRuleRepository ruleRepo;
    @Autowired private RelayConfigRepository relayRepo;
    @Autowired private MqIngestionStrategy ingestion;
    @Autowired private MockDeliveryAdapter mockAdapter;
    @Autowired private RoutingService routingService;
    @Autowired private RuleLoaderService ruleLoaderService;

    @BeforeEach
    void setUp() {
        mockAdapter.clear();
    }

    @Test
    @Order(1)
    void singlePageMt940_endToEnd() {
        routingService.refreshCache();

        ingestion.onMessage("""
                {1:F01HSBCGB2LAXXX0000000000}{2:I940CITIUS33XXXXN}{4:
                :20:E2EREF01
                :25:123456789
                :28C:00001/001
                :60F:C210101EUR1000,
                :61:2101010101DR100,
                :62F:C210101EUR900,
                -}""");

        List<DeliveryRecord> deliveries = mockAdapter.getDeliveries();

        boolean hasReportingQ1 = deliveries.stream()
                .anyMatch(d -> "RECON.INTELLIMATCH.IN".equals(d.getDestination())
                        && "123456789".equals(d.getAccountNumber()));

        assertTrue(hasReportingQ1, "Expected delivery to RECON.INTELLIMATCH.IN, got: " + deliveries.stream()
                .map(d -> d.getDestination() + "/" + d.getAccountNumber()).toList());

        // Relay config is for DEUTDEFF/BNPAFRPP, not HSBCGB2L/CITIUS33 — no relay expected
        boolean hasSwiftRelay = deliveries.stream()
                .anyMatch(d -> "SWIFT.ALLIANCE.OUTBOUND".equals(d.getDestination()));
        assertFalse(hasSwiftRelay, "No relay expected for HSBCGB2L/CITIUS33 BIC pair");

        // Verify ODS audit trail was created
        List<MtMessageOds> odsMessages = odsRepo.findAll();
        assertTrue(odsMessages.stream().anyMatch(m -> m.getStatus() == OdsStatus.COMPLETED),
                "ODS audit record should be marked COMPLETED");
    }

    @Test
    @Order(2)
    void multiPageMt940_aggregationAndDelivery() {
        routingService.refreshCache();

        // Send page 1 of 2 (HSBC UK → Citi US)
        ingestion.onMessage("""
                {1:F01HSBCGB2LAXXX0000000000}{2:I940CITIUS33XXXXN}{4:
                :20:MPREF01
                :25:123456789
                :28C:00010/001
                :60F:C210201EUR5000,
                :61:2102010201DR200,
                :62M:C210201EUR4800,
                -}""");

        // Send page 2 of 2 (HSBC UK → Citi US)
        ingestion.onMessage("""
                {1:F01HSBCGB2LAXXX0000000000}{2:I940CITIUS33XXXXN}{4:
                :20:MPREF01
                :25:123456789
                :28C:00010/002
                :60M:C210201EUR4800,
                :61:2102010201CR1000,
                :62F:C210201EUR5800,
                -}""");

        List<DeliveryRecord> deliveries = mockAdapter.getDeliveries();
        assertFalse(deliveries.isEmpty(), "Expected deliveries after multi-page aggregation");

        boolean hasReporting = deliveries.stream()
                .anyMatch(d -> "RECON.INTELLIMATCH.IN".equals(d.getDestination()));
        assertTrue(hasReporting, "Expected delivery to RECON.INTELLIMATCH.IN for multi-page MT940");

        // Verify combined message has single header, merged transactions, no intermediate balances
        DeliveryRecord delivered = deliveries.stream()
                .filter(d -> "RECON.INTELLIMATCH.IN".equals(d.getDestination()))
                .findFirst().orElseThrow();
        String raw = delivered.getRawMessage();
        // Single header from page 1
        assertTrue(raw.startsWith("{1:F01HSBCGB2L"), "Combined should have single Block 1 header");
        assertEquals(1, raw.split("\\{1:").length - 1, "Should have exactly one Block 1 header");
        assertEquals(1, raw.split("\\{2:").length - 1, "Should have exactly one Block 2 header");
        // Opening balance from page 1, closing from page 2
        assertTrue(raw.contains(":60F:"), "Combined should have :60F: from first page");
        assertTrue(raw.contains(":62F:"), "Combined should have :62F: from last page");
        // No intermediate balance tags
        assertFalse(raw.contains(":60M:"), "Combined should NOT have intermediate opening :60M:");
        assertFalse(raw.contains(":62M:"), "Combined should NOT have intermediate closing :62M:");
        // Transactions from both pages
        assertTrue(raw.contains("DR200,"), "Combined should have transaction from page 1");
        assertTrue(raw.contains("CR1000,"), "Combined should have transaction from page 2");

        // Verify all related ODS records are COMPLETED
        List<MtMessageOds> allOds = odsRepo.findAll();
        long completedCount = allOds.stream().filter(m -> m.getStatus() == OdsStatus.COMPLETED).count();
        assertTrue(completedCount >= 2, "All aggregated pages should be marked COMPLETED");
    }

    @Test
    @Order(3)
    void mt942_routesAndRelays() {
        RoutingRule rule = new RoutingRule();
        rule.setAccountNumber("987654321");
        rule.setMessageType("MT942");
        rule.setSenderBic("DEUTDEFF");
        rule.setReceiverBic("BNPAFRPP");
        rule.setDestinationQueue("CASH.CALYPSO.INTRADAY");
        rule.setActive(true);
        rule.setSource(RuleSource.UI);
        ruleRepo.save(rule);
        routingService.refreshCache();

        ingestion.onMessage("""
                {1:F01DEUTDEFFAXXX0000000000}{2:I942BNPAFRPPXXXXN}{4:
                :20:MT942REF
                :25:987654321
                :28C:00001/001
                :34F:EUR100,
                :13D:2101011200+0100
                :61:2101010101CR500,
                :90D:1EUR100,
                :90C:1EUR500,
                -}""");

        List<DeliveryRecord> deliveries = mockAdapter.getDeliveries();
        boolean hasIntradayQ1 = deliveries.stream()
                .anyMatch(d -> "CASH.CALYPSO.INTRADAY".equals(d.getDestination()));
        assertTrue(hasIntradayQ1, "Expected delivery to CASH.CALYPSO.INTRADAY for MT942, got: " +
                deliveries.stream().map(DeliveryRecord::getDestination).toList());

        // Relay config matches DEUTDEFF/BNPAFRPP — relay expected with BIC replaced to COBADEFF
        DeliveryRecord relayDelivery = deliveries.stream()
                .filter(d -> "SWIFT.ALLIANCE.OUTBOUND".equals(d.getDestination()))
                .findFirst()
                .orElse(null);
        assertNotNull(relayDelivery, "Expected relay to SWIFT.ALLIANCE.OUTBOUND for DEUTDEFF/BNPAFRPP BIC pair");

        // Verify receiver BIC was replaced in the relayed message
        String relayRaw = relayDelivery.getRawMessage();
        assertTrue(relayRaw.contains("COBADEFF"), "Relayed message should have receiver BIC replaced to COBADEFF");
        assertFalse(relayRaw.contains("BNPAFRPP"), "Relayed message should NOT contain original receiver BIC BNPAFRPP");
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
