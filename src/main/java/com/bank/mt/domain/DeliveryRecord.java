package com.bank.mt.domain;

import java.time.LocalDateTime;

/**
 * Captured delivery event used by MockDeliveryAdapter for testing.
 */
public class DeliveryRecord {

    private final String destination;
    private final String messageType;
    private final String accountNumber;
    private final String transactionReference;
    private final String rawMessage;
    private final LocalDateTime deliveredAt;

    public DeliveryRecord(String destination, MtStatement statement) {
        this.destination = destination;
        this.messageType = statement.getMessageType();
        this.accountNumber = statement.getAccountNumber();
        this.transactionReference = statement.getTransactionReference();
        this.rawMessage = statement.getRawMessage();
        this.deliveredAt = LocalDateTime.now();
    }

    public String getDestination() { return destination; }
    public String getMessageType() { return messageType; }
    public String getAccountNumber() { return accountNumber; }
    public String getTransactionReference() { return transactionReference; }
    public String getRawMessage() { return rawMessage; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
}
