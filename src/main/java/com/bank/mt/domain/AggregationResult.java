package com.bank.mt.domain;

/**
 * Result of the aggregation step — determines whether the statement
 * is ready for routing, was rejected, or is still awaiting more pages.
 */
public class AggregationResult {

    private final boolean rejected;
    private final boolean readyForRouting;
    private final MtStatement combinedStatement;

    private AggregationResult(boolean rejected, boolean readyForRouting, MtStatement combinedStatement) {
        this.rejected = rejected;
        this.readyForRouting = readyForRouting;
        this.combinedStatement = combinedStatement;
    }

    public static AggregationResult ready(MtStatement statement) {
        return new AggregationResult(false, true, statement);
    }

    public static AggregationResult pending() {
        return new AggregationResult(false, false, null);
    }

    public static AggregationResult rejected() {
        return new AggregationResult(true, false, null);
    }

    public boolean isRejected() { return rejected; }
    public boolean isReadyForRouting() { return readyForRouting; }
    public MtStatement getCombinedStatement() { return combinedStatement; }
}
