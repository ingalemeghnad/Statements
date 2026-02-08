package com.bank.mt.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mt_aggregation")
public class MtAggregation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "statement_number")
    private String statementNumber;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "message_type")
    private String messageType;

    @Column(name = "total_pages")
    private int totalPages;

    @Column(name = "received_pages")
    private int receivedPages;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private AggregationStatus status;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "aggregation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pageNumber ASC")
    private List<MtAggregationPage> pages = new ArrayList<>();

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

    public String getStatementNumber() { return statementNumber; }
    public void setStatementNumber(String statementNumber) { this.statementNumber = statementNumber; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public int getReceivedPages() { return receivedPages; }
    public void setReceivedPages(int receivedPages) { this.receivedPages = receivedPages; }

    public AggregationStatus getStatus() { return status; }
    public void setStatus(AggregationStatus status) { this.status = status; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<MtAggregationPage> getPages() { return pages; }
    public void setPages(List<MtAggregationPage> pages) { this.pages = pages; }

    public void addPage(MtAggregationPage page) {
        pages.add(page);
        page.setAggregation(this);
    }
}
