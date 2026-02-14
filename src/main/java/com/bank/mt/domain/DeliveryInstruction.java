package com.bank.mt.domain;

import java.util.List;

/**
 * Output of the routing module — lists downstream destinations
 * and optional SWIFT relay with receiver BIC replacement.
 */
public class DeliveryInstruction {

    private final List<String> downstreamDestinations;
    private final String swiftReceiverBic; // null = no relay, non-null = relay with this BIC
    private final MtStatement statement;

    public DeliveryInstruction(List<String> downstreamDestinations, String swiftReceiverBic, MtStatement statement) {
        this.downstreamDestinations = downstreamDestinations;
        this.swiftReceiverBic = swiftReceiverBic;
        this.statement = statement;
    }

    public List<String> getDownstreamDestinations() { return downstreamDestinations; }
    public boolean isRelayToSwift() { return swiftReceiverBic != null; }
    public String getSwiftReceiverBic() { return swiftReceiverBic; }
    public MtStatement getStatement() { return statement; }
}
