package com.sgd_hc.notifications.repository;

import com.sgd_hc.notifications.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdAndTenantIdOrderByCreatedAtDesc(UUID userId, UUID tenantId, Pageable pageable);

    long countByUserIdAndTenantIdAndIsReadFalse(UUID userId, UUID tenantId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.user.id = :userId AND n.tenantId = :tenantId AND n.isRead = false")
    int markAllAsRead(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);

    boolean existsByTenantIdAndTypeAndCreatedAtAfter(UUID tenantId, com.sgd_hc.notifications.entity.NotificationType type, java.time.OffsetDateTime since);

    @Query("SELECT COUNT(n) > 0 FROM Notification n WHERE n.tenantId = :tenantId AND n.type = :type AND n.message LIKE %:keyword% AND n.createdAt > :since")
    boolean existsByTenantIdAndTypeAndMessageContainingAndCreatedAtAfter(
            @Param("tenantId") UUID tenantId, 
            @Param("type") com.sgd_hc.notifications.entity.NotificationType type, 
            @Param("keyword") String keyword, 
            @Param("since") java.time.OffsetDateTime since);
}
