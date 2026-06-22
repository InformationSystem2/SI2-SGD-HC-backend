package com.sgd_hc.workflow.repository;

import com.sgd_hc.workflow.entity.ReviewTask;
import com.sgd_hc.workflow.entity.ReviewTaskStatus;
import com.sgd_hc.workflow.entity.TaskDelegation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewTaskRepository extends JpaRepository<ReviewTask, UUID> {

    Optional<ReviewTask> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ReviewTask> findByAssignedToIdAndTenantId(UUID assignedToId, UUID tenantId);

    List<ReviewTask> findByAssignedToIdAndStatusAndTenantId(UUID assignedToId, ReviewTaskStatus status, UUID tenantId);

    List<ReviewTask> findByDocumentIdAndTenantId(UUID documentId, UUID tenantId);

    List<ReviewTask> findByDocumentIdAndStatusAndTenantId(UUID documentId, ReviewTaskStatus status, UUID tenantId);

    List<ReviewTask> findByWorkflowIdOrderByCreatedAtAsc(UUID workflowId);

    List<ReviewTask> findByWorkflowIdAndDocumentId(UUID workflowId, UUID documentId);

    List<ReviewTask> findByTenantIdAndStatusAndDueDateBefore(UUID tenantId, ReviewTaskStatus status, OffsetDateTime dueDate);

    @Query("""
        SELECT COUNT(rt) FROM ReviewTask rt
        WHERE rt.tenantId = :tenantId AND rt.status = :status
    """)
    long countByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") ReviewTaskStatus status);

    @Query("""
        SELECT COUNT(rt) FROM ReviewTask rt
        WHERE rt.tenantId = :tenantId
        AND rt.status = com.sgd_hc.workflow.entity.ReviewTaskStatus.COMPLETED
        AND rt.completedAt >= :since
    """)
    long countCompletedSince(@Param("tenantId") UUID tenantId, @Param("since") OffsetDateTime since);

    @Query("""
        SELECT rt FROM ReviewTask rt
        WHERE rt.tenantId = :tenantId
        AND rt.status = com.sgd_hc.workflow.entity.ReviewTaskStatus.COMPLETED
        AND rt.startedAt IS NOT NULL AND rt.completedAt IS NOT NULL
    """)
    List<ReviewTask> findCompletedWithTiming(@Param("tenantId") UUID tenantId);

    @Query("""
        SELECT COUNT(rt) FROM ReviewTask rt
        WHERE rt.tenantId = :tenantId
        AND rt.createdAt >= :since
    """)
    long countCreatedSince(@Param("tenantId") UUID tenantId, @Param("since") OffsetDateTime since);

    @Query("""
        SELECT COUNT(rt) FROM ReviewTask rt
        WHERE rt.tenantId = :tenantId
        AND rt.status = com.sgd_hc.workflow.entity.ReviewTaskStatus.PENDING
        AND rt.dueDate IS NOT NULL AND rt.dueDate < CURRENT_TIMESTAMP
    """)
    long countOverdue(@Param("tenantId") UUID tenantId);

    @Query("""
        SELECT rt FROM ReviewTask rt
        JOIN TaskDelegation td ON rt.assignedTo.id = td.delegator.id
        WHERE rt.tenantId = :tenantId
        AND td.delegate.id = :delegateId
        AND td.isActive = true
        AND td.startDate <= CURRENT_DATE
        AND (td.endDate IS NULL OR td.endDate >= CURRENT_DATE)
        AND rt.status IN (com.sgd_hc.workflow.entity.ReviewTaskStatus.PENDING, com.sgd_hc.workflow.entity.ReviewTaskStatus.IN_PROGRESS)
    """)
    List<ReviewTask> findDelegatedTasks(@Param("tenantId") UUID tenantId, @Param("delegateId") UUID delegateId);
}
