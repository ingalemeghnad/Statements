package com.bank.mt.repository;

import com.bank.mt.domain.RelayConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelayConfigRepository extends JpaRepository<RelayConfig, Long> {

    List<RelayConfig> findByActiveTrue();
}
