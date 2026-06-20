package com.sgd_hc.workflow.repository;

import com.sgd_hc.workflow.entity.ReviewTask;
import com.sgd_hc.workflow.entity.ReviewTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<ReviewTask> findByTenantIdAndStatusAndDueDateBefore(UUID tenantId, ReviewTaskStatus status, OffsetDateTime dueDate);
}
