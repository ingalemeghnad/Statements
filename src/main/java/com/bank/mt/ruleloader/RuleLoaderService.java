package com.bank.mt.ruleloader;

import com.bank.mt.domain.RoutingRule;
import com.bank.mt.domain.RuleSource;
import com.bank.mt.repository.RoutingRuleRepository;
import com.bank.mt.routing.RoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Loads routing rules from a CSV file.
 * Only replaces FILE-sourced rules; UI rules are preserved.
 * Triggers routing cache refresh after load.
 */
@Service
public class RuleLoaderService {

    private static final Logger log = LoggerFactory.getLogger(RuleLoaderService.class);

    private final RoutingRuleRepository ruleRepository;
    private final RoutingService routingService;
    private final ResourceLoader resourceLoader;

    @Value("${mt.routing.rules-file-path:classpath:rules/routing-rules.csv}")
    private String rulesFilePath;

    public RuleLoaderService(RoutingRuleRepository ruleRepository,
                              RoutingService routingService,
                              ResourceLoader resourceLoader) {
        this.ruleRepository = ruleRepository;
        this.routingService = routingService;
        this.resourceLoader = resourceLoader;
    }

    @Transactional
    public int loadRulesFromFile() {
        return loadRulesFromPath(rulesFilePath);
    }

    @Transactional
    public int loadRulesFromPath(String path) {
        log.info("Loading routing rules from: {}", path);

        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            log.warn("Rules file not found: {}", path);
            return 0;
        }

        List<RoutingRule> rules = new ArrayList<>();
        String batchId = UUID.randomUUID().toString();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine(); // skip header
            if (header == null) {
                return 0;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",", -1);
                if (parts.length < 5) {
                    log.warn("Skipping malformed CSV line: {}", line);
                    continue;
                }

                RoutingRule rule = new RoutingRule();
                rule.setAccountNumber(parts[0].trim());
                rule.setMessageType(parts[1].trim());
                rule.setSenderBic(parts[2].trim());
                rule.setReceiverBic(parts[3].trim());
                rule.setDestinationQueue(parts[4].trim());
                rule.setActive(true);
                rule.setBatchId(batchId);
                rule.setSource(RuleSource.FILE);
                rules.add(rule);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read rules file: " + path, e);
        }

        // Transactional batch replace: delete existing FILE rules, then insert new ones
        ruleRepository.deleteBySource(RuleSource.FILE);
        ruleRepository.saveAll(rules);

        log.info("Loaded {} FILE rules (batch={})", rules.size(), batchId);

        // Refresh the routing cache
        routingService.refreshCache();

        return rules.size();
    }
}
