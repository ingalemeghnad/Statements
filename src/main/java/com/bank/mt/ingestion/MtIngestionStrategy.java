package com.bank.mt.ingestion;

/**
 * Pluggable ingestion strategy for reading MT messages from ODS.
 * Implementations are selected via mt.ingestion.mode property.
 */
public interface MtIngestionStrategy {

    void start();
}
