package com.bank.mt.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * POC stub for event-driven ingestion (e.g., via CDC / Kafka / DB triggers).
 * Not implemented — activate by setting mt.ingestion.mode=EVENT.
 */
@Component
@ConditionalOnProperty(name = "mt.ingestion.mode", havingValue = "EVENT")
public class EventDrivenOdsIngestionStrategy implements MtIngestionStrategy {

    private static final Logger log = LoggerFactory.getLogger(EventDrivenOdsIngestionStrategy.class);

    @Override
    public void start() {
        log.info("Event-driven ingestion strategy is a stub — no-op in POC mode");
    }
}
