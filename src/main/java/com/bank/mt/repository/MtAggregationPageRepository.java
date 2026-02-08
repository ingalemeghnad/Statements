package com.bank.mt.repository;

import com.bank.mt.domain.MtAggregationPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MtAggregationPageRepository extends JpaRepository<MtAggregationPage, Long> {

    boolean existsByAggregationIdAndChecksum(Long aggregationId, String checksum);
}
