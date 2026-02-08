package com.bank.mt.routing;

import com.bank.mt.domain.*;
import com.bank.mt.repository.RelayConfigRepository;
import com.bank.mt.repository.RoutingBicExclusionRepository;
import com.bank.mt.repository.RoutingRuleRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Evaluates routing rules and relay configuration independently.
 * Maintains in-memory caches for fast lookup, refreshed on rule changes.
 */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final RoutingRuleRepository ruleRepository;
    private final RelayConfigRepository relayRepository;
    private final RoutingBicExclusionRepository exclusionRepository;
    private final Counter cacheHitCounter;
    private final Counter exceptionQueueCounter;
    private final Counter skippedCounter;

    @Value("${mt.routing.exception-queue:EXCEPTION.QUEUE}")
    private String exceptionQueue;

    private volatile List<RoutingRule> cachedRules = new CopyOnWriteArrayList<>();
    private volatile List<RelayConfig> cachedRelays = new CopyOnWriteArrayList<>();
    private volatile Set<String> cachedExcludedBranches = Set.of();

    public RoutingService(RoutingRuleRepository ruleRepository,
                           RelayConfigRepository relayRepository,
                           RoutingBicExclusionRepository exclusionRepository,
                           MeterRegistry meterRegistry) {
        this.ruleRepository = ruleRepository;
        this.relayRepository = relayRepository;
        this.exclusionRepository = exclusionRepository;
        this.cacheHitCounter = meterRegistry.counter("mt.routing.cache.hit");
        this.exceptionQueueCounter = meterRegistry.counter("mt.routing.exception.queue");
        this.skippedCounter = meterRegistry.counter("mt.routing.skipped");
    }

    @PostConstruct
    public void refreshCache() {
        cachedRules = new CopyOnWriteArrayList<>(ruleRepository.findByActiveTrue());
        cachedRelays = new CopyOnWriteArrayList<>(relayRepository.findByActiveTrue());
        cachedExcludedBranches = exclusionRepository.findByActiveTrue().stream()
                .map(e -> e.getBranchCode().toUpperCase())
                .collect(Collectors.toSet());
        log.info("Routing cache refreshed: {} rules, {} relay configs, {} excluded branches",
                cachedRules.size(), cachedRelays.size(), cachedExcludedBranches.size());
    }

    public DeliveryInstruction route(MtStatement statement) {
        String branch = statement.getReceiverBicBranch();
        if (branch != null && !branch.isBlank() && cachedExcludedBranches.contains(branch.toUpperCase())) {
            log.info("Routing skipped for ref={}: branch {} is excluded",
                    statement.getTransactionReference(), branch);
            skippedCounter.increment();
            return new DeliveryInstruction(List.of(), false, statement);
        }

        List<String> destinations = evaluateRoutingRules(statement);
        boolean relay = evaluateRelayConfig(statement);

        log.info("Routing result for ref={}: destinations={}, relay={}",
                statement.getTransactionReference(), destinations, relay);

        return new DeliveryInstruction(destinations, relay, statement);
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
            log.warn("No routing rule matched for ref={} acct={} type={} — routing to exception queue [{}]",
                    statement.getTransactionReference(),
                    statement.getAccountNumber(),
                    statement.getMessageType(),
                    exceptionQueue);
            destinations.add(exceptionQueue);
            exceptionQueueCounter.increment();
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

    private boolean evaluateRelayConfig(MtStatement statement) {
        for (RelayConfig relay : cachedRelays) {
            if (matchesField(relay.getAccountNumber(), statement.getAccountNumber())
                    && matchesField(relay.getSenderBic(), statement.getSenderBic())
                    && matchesField(relay.getReceiverBic(), statement.getReceiverBic())) {
                return true;
            }
        }
        return false;
    }
}
