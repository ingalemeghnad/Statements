package com.bank.mt.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "routing_rule")
public class RoutingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "message_type")
    private String messageType;

    @Column(name = "sender_bic")
    private String senderBic;

    @Column(name = "receiver_bic")
    private String receiverBic;

    @Column(name = "destination_queue")
    private String destinationQueue;

    @Column(name = "active")
    private boolean active;

    @Column(name = "batch_id")
    private String batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private RuleSource source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getSenderBic() { return senderBic; }
    public void setSenderBic(String senderBic) { this.senderBic = senderBic; }

    public String getReceiverBic() { return receiverBic; }
    public void setReceiverBic(String receiverBic) { this.receiverBic = receiverBic; }

    public String getDestinationQueue() { return destinationQueue; }
    public void setDestinationQueue(String destinationQueue) { this.destinationQueue = destinationQueue; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public RuleSource getSource() { return source; }
    public void setSource(RuleSource source) { this.source = source; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
