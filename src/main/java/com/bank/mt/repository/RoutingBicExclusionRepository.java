package com.bank.mt.repository;

import com.bank.mt.domain.RoutingBicExclusion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoutingBicExclusionRepository extends JpaRepository<RoutingBicExclusion, Long> {

    List<RoutingBicExclusion> findByActiveTrue();
}
