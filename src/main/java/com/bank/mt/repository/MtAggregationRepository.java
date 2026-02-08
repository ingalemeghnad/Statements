package com.bank.mt.repository;

import com.bank.mt.domain.AggregationStatus;
import com.bank.mt.domain.MtAggregation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MtAggregationRepository extends JpaRepository<MtAggregation, Long> {

    Optional<MtAggregation> findByStatementNumberAndAccountNumberAndMessageTypeAndTransactionReference(
            String statementNumber, String accountNumber, String messageType, String transactionReference);

    @Query("SELECT a FROM MtAggregation a WHERE a.status = :status AND a.createdAt < :cutoff")
    List<MtAggregation> findExpiredAggregations(@Param("status") AggregationStatus status,
                                                 @Param("cutoff") LocalDateTime cutoff);
}
