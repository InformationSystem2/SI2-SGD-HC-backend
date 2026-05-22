package com.sgd_hc.tenants.repository;

import com.sgd_hc.tenants.entity.SubscriptionStatus;
import com.sgd_hc.tenants.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findBySlug(String slug);

    @Query("""
        SELECT t FROM tenants t
        WHERE (
            LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(t.slug) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(t.email) LIKE LOWER(CONCAT('%', :search, '%'))
        )
        ORDER BY t.createdAt DESC
    """)
    Page<Tenant> findAllSearch(
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
        SELECT t FROM tenants t
        WHERE t.subscriptionStatus = :status
        ORDER BY t.createdAt DESC
    """)
    Page<Tenant> findAllByStatus(
            @Param("status") SubscriptionStatus status,
            Pageable pageable
    );

    @Query("""
        SELECT t FROM tenants t
        WHERE t.subscriptionStatus = :status
        AND (
            LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(t.slug) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(t.email) LIKE LOWER(CONCAT('%', :search, '%'))
        )
        ORDER BY t.createdAt DESC
    """)
    Page<Tenant> findAllByStatusAndSearch(
            @Param("status") SubscriptionStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT COUNT(u) FROM users u WHERE u.tenant.id = :tenantId AND u.isActive = true")
    int countActiveUsersByTenantId(@Param("tenantId") UUID tenantId);

    List<Tenant> findAllBySubscriptionStatus(SubscriptionStatus status);
}