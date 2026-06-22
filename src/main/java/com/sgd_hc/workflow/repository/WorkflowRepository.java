package com.sgd_hc.workflow.repository;

import com.sgd_hc.workflow.entity.Workflow;
import com.sgd_hc.workflow.entity.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Optional<Workflow> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Workflow> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, WorkflowStatus status);

    List<Workflow> findByAssigneeIdAndTenantIdOrderByCreatedAtDesc(UUID assigneeId, UUID tenantId);

    List<Workflow> findByCreatorIdAndTenantIdOrderByCreatedAtDesc(UUID creatorId, UUID tenantId);

    @Query("""
            SELECT w FROM Workflow w
            WHERE w.tenantId = :tenantId
              AND w.assignee.id = :userId
              AND w.status = :status
            ORDER BY w.createdAt DESC
            """)
    List<Workflow> findByAssigneeAndStatus(@Param("tenantId") UUID tenantId,
                                           @Param("userId") UUID userId,
                                           @Param("status") WorkflowStatus status);

    long countByTenantIdAndStatus(UUID tenantId, WorkflowStatus status);

    long countByAssigneeIdAndTenantIdAndStatus(UUID assigneeId, UUID tenantId, WorkflowStatus status);

    long countByCreatorIdAndTenantIdAndStatus(UUID creatorId, UUID tenantId, WorkflowStatus status);

    @Query("""
            SELECT w FROM Workflow w
            WHERE w.tenantId = :tenantId
              AND w.status = 'ACTIVE'
              AND w.dueDate IS NOT NULL
              AND w.dueDate < CURRENT_TIMESTAMP
            """)
    List<Workflow> findOverdueWorkflows(@Param("tenantId") UUID tenantId);
}
