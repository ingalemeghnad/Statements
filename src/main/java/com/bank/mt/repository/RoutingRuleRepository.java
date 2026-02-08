package com.bank.mt.repository;

import com.bank.mt.domain.RoutingRule;
import com.bank.mt.domain.RuleSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {

    List<RoutingRule> findByActiveTrue();

    @Modifying
    @Query("DELETE FROM RoutingRule r WHERE r.source = :source")
    void deleteBySource(@Param("source") RuleSource source);
}
