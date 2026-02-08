package com.bank.mt.scheduler;

import com.bank.mt.ruleloader.RuleLoaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the CSV rule loader daily.
 */
@Component
public class RuleLoaderScheduler {

    private static final Logger log = LoggerFactory.getLogger(RuleLoaderScheduler.class);

    private final RuleLoaderService ruleLoaderService;

    public RuleLoaderScheduler(RuleLoaderService ruleLoaderService) {
        this.ruleLoaderService = ruleLoaderService;
    }

    @Scheduled(cron = "0 0 2 * * *") // daily at 2 AM
    public void loadRulesDaily() {
        log.info("Scheduled daily rule file reload");
        int count = ruleLoaderService.loadRulesFromFile();
        log.info("Daily rule reload complete: {} rules loaded", count);
    }
}
