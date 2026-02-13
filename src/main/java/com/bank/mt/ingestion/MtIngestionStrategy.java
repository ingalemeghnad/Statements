package com.bank.mt.ingestion;

/**
 * Pluggable ingestion strategy for receiving MT messages.
 * Implementations are selected via mt.ingestion.mode property.
 */
public interface MtIngestionStrategy {

    void start();
}
