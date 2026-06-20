package com.sgd_hc.backups.repository;

import com.sgd_hc.backups.entity.BackupHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BackupHistoryRepository extends JpaRepository<BackupHistory, UUID> {

    @Query(value = "SELECT COUNT(*) FROM backup_history " +
            "WHERE tenant_id = :tenantId " +
            "AND EXTRACT(YEAR FROM created_at) = :year", nativeQuery = true)
    long countByTenantIdAndYear(@Param("tenantId") UUID tenantId, @Param("year") int year);
}
