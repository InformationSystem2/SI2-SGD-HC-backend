package com.sgd_hc.tenants.repository;

import com.sgd_hc.tenants.entity.ApiCallUsage;
import com.sgd_hc.users.entity.RootEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApiCallUsageRepository extends JpaRepository<ApiCallUsage, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO api_call_usage (id, tenant_id, year_month, call_count, created_at, updated_at)
            VALUES (uuid_generate_v4(), :tenantId, :yearMonth, 1, now(), now())
            ON CONFLICT (tenant_id, year_month)
            DO UPDATE SET call_count = api_call_usage.call_count + 1,
                         updated_at = now()
            """, nativeQuery = true)
    void incrementCallCount(@Param("tenantId") UUID tenantId, @Param("yearMonth") String yearMonth);

    @Query(value = """
            SELECT COALESCE(call_count, 0) FROM api_call_usage
            WHERE tenant_id = :tenantId AND year_month = :yearMonth
            """, nativeQuery = true)
    long getCallCount(@Param("tenantId") UUID tenantId, @Param("yearMonth") String yearMonth);
}
