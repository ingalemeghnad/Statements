package com.bank.mt.domain;

import java.util.List;

/**
 * Result of the aggregation step — determines whether the statement
 * is ready for routing, was rejected, or is still awaiting more pages.
 */
public class AggregationResult {

    private final boolean rejected;
    private final boolean readyForRouting;
    private final MtStatement combinedStatement;
    private final List<Long> relatedOdsMessageIds;

    private AggregationResult(boolean rejected,
                              boolean readyForRouting,
                              MtStatement combinedStatement,
                              List<Long> relatedOdsMessageIds) {
        this.rejected = rejected;
        this.readyForRouting = readyForRouting;
        this.combinedStatement = combinedStatement;
        this.relatedOdsMessageIds = relatedOdsMessageIds;
    }

    public static AggregationResult ready(MtStatement statement, List<Long> relatedOdsMessageIds) {
        return new AggregationResult(false, true, statement, relatedOdsMessageIds);
    }

    public static AggregationResult pending() {
        return new AggregationResult(false, false, null, List.of());
    }

    public static AggregationResult rejected() {
        return new AggregationResult(true, false, null, List.of());
    }

    public boolean isRejected() { return rejected; }
    public boolean isReadyForRouting() { return readyForRouting; }
    public MtStatement getCombinedStatement() { return combinedStatement; }
    public List<Long> getRelatedOdsMessageIds() { return relatedOdsMessageIds; }
}
