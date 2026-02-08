package com.bank.mt.domain;

import java.util.List;

/**
 * Output of the routing module — lists downstream destinations
 * and whether the message should also be relayed to SWIFT.
 */
public class DeliveryInstruction {

    private final List<String> downstreamDestinations;
    private final boolean relayToSwift;
    private final MtStatement statement;

    public DeliveryInstruction(List<String> downstreamDestinations, boolean relayToSwift, MtStatement statement) {
        this.downstreamDestinations = downstreamDestinations;
        this.relayToSwift = relayToSwift;
        this.statement = statement;
    }

    public List<String> getDownstreamDestinations() { return downstreamDestinations; }
    public boolean isRelayToSwift() { return relayToSwift; }
    public MtStatement getStatement() { return statement; }
}
