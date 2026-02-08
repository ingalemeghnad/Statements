package com.bank.mt.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mt_aggregation_page",
       uniqueConstraints = @UniqueConstraint(columnNames = {"aggregation_id", "page_number"}))
public class MtAggregationPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aggregation_id", nullable = false)
    private MtAggregation aggregation;

    @Column(name = "page_number")
    private int pageNumber;

    @Column(name = "raw_message", columnDefinition = "TEXT")
    private String rawMessage;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "ods_message_id")
    private Long odsMessageId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MtAggregation getAggregation() { return aggregation; }
    public void setAggregation(MtAggregation aggregation) { this.aggregation = aggregation; }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public Long getOdsMessageId() { return odsMessageId; }
    public void setOdsMessageId(Long odsMessageId) { this.odsMessageId = odsMessageId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
