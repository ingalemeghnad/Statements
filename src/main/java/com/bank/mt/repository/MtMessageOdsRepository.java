package com.bank.mt.repository;

import com.bank.mt.domain.MtMessageOds;
import com.bank.mt.domain.OdsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MtMessageOdsRepository extends JpaRepository<MtMessageOds, Long> {

    /**
     * Fetch a batch of NEW messages for processing.
     * H2 does not support FOR UPDATE SKIP LOCKED, so we use a simple query here.
     * In production (PostgreSQL), this would use: FOR UPDATE SKIP LOCKED
     */
    @Query("SELECT m FROM MtMessageOds m WHERE m.status = :status ORDER BY m.id")
    List<MtMessageOds> findByStatusOrderByIdLimit(@Param("status") OdsStatus status,
                                                   org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("UPDATE MtMessageOds m SET m.status = :newStatus, m.updatedAt = CURRENT_TIMESTAMP WHERE m.id IN :ids AND m.status = :currentStatus")
    int updateStatusBatch(@Param("ids") List<Long> ids,
                          @Param("currentStatus") OdsStatus currentStatus,
                          @Param("newStatus") OdsStatus newStatus);
}
