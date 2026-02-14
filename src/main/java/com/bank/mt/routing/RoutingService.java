package com.bank.mt.routing;

import com.bank.mt.domain.*;
import com.bank.mt.repository.RelayConfigRepository;
import com.bank.mt.repository.RoutingRuleRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Evaluates routing rules and relay configuration independently.
 * Maintains in-memory caches for fast lookup, refreshed on rule changes.
 */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final RoutingRuleRepository ruleRepository;
    private final RelayConfigRepository relayRepository;
    private final Counter cacheHitCounter;

    private volatile List<RoutingRule> cachedRules = new CopyOnWriteArrayList<>();
    private volatile List<RelayConfig> cachedRelays = new CopyOnWriteArrayList<>();

    public RoutingService(RoutingRuleRepository ruleRepository,
                           RelayConfigRepository relayRepository,
                           MeterRegistry meterRegistry) {
        this.ruleRepository = ruleRepository;
        this.relayRepository = relayRepository;
        this.cacheHitCounter = meterRegistry.counter("mt.routing.cache.hit");
    }

    @PostConstruct
    public void refreshCache() {
        cachedRules = new CopyOnWriteArrayList<>(ruleRepository.findByActiveTrue());
        cachedRelays = new CopyOnWriteArrayList<>(relayRepository.findByActiveTrue());
        log.info("Routing cache refreshed: {} rules, {} relay configs",
                cachedRules.size(), cachedRelays.size());
    }

    public DeliveryInstruction route(MtStatement statement) {
        List<String> destinations = evaluateRoutingRules(statement);
        String swiftReceiverBic = evaluateRelayConfig(statement);

        log.info("Routing result for ref={}: destinations={}, swiftRelay={}",
                statement.getTransactionReference(), destinations,
                swiftReceiverBic != null ? swiftReceiverBic : "none");

        return new DeliveryInstruction(destinations, swiftReceiverBic, statement);
    }

    private List<String> evaluateRoutingRules(MtStatement statement) {
        List<String> destinations = new ArrayList<>();

        for (RoutingRule rule : cachedRules) {
            if (matches(rule, statement)) {
                destinations.add(rule.getDestinationQueue());
                if (rule.getSecondaryDestinations() != null && !rule.getSecondaryDestinations().isBlank()) {
                    for (String sec : rule.getSecondaryDestinations().split(",")) {
                        String trimmed = sec.trim();
                        if (!trimmed.isEmpty()) {
                            destinations.add(trimmed);
                        }
                    }
                }
                cacheHitCounter.increment();
            }
        }

        if (destinations.isEmpty()) {
            log.warn("No routing rule matched for ref={} acct={} type={}",
                    statement.getTransactionReference(),
                    statement.getAccountNumber(),
                    statement.getMessageType());
        }

        return destinations;
    }

    private boolean matches(RoutingRule rule, MtStatement statement) {
        return matchesField(rule.getAccountNumber(), statement.getAccountNumber())
                && matchesField(rule.getMessageType(), statement.getMessageType())
                && matchesField(rule.getSenderBic(), statement.getSenderBic())
                && matchesField(rule.getReceiverBic(), statement.getReceiverBic());
    }

    private boolean matchesField(String ruleValue, String actualValue) {
        if (ruleValue == null || ruleValue.isBlank() || "*".equals(ruleValue)) {
            return true; // wildcard
        }
        return ruleValue.equalsIgnoreCase(actualValue);
    }

    /**
     * Evaluates relay config. Returns the SWIFT receiver BIC to use for relay,
     * or null if no relay config matches.
     */
    private String evaluateRelayConfig(MtStatement statement) {
        for (RelayConfig relay : cachedRelays) {
            if (matchesField(relay.getAccountNumber(), statement.getAccountNumber())
                    && matchesField(relay.getSenderBic(), statement.getSenderBic())
                    && matchesField(relay.getReceiverBic(), statement.getReceiverBic())) {
                return relay.getSwiftReceiverBic();
            }
        }
        return null;
    }
}
