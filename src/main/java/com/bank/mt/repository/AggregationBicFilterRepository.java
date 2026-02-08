package com.bank.mt.repository;

import com.bank.mt.domain.AggregationBicFilter;
import com.bank.mt.domain.AggregationFilterType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AggregationBicFilterRepository extends JpaRepository<AggregationBicFilter, Long> {

    List<AggregationBicFilter> findByFilterTypeAndActiveTrue(AggregationFilterType filterType);

    List<AggregationBicFilter> findByActiveTrue();
}
