package com.sgd_hc.workflow.repository;

import com.sgd_hc.workflow.entity.WorkflowComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowCommentRepository extends JpaRepository<WorkflowComment, UUID> {

    List<WorkflowComment> findByDocumentIdAndTenantIdOrderByCreatedAtAsc(UUID documentId, UUID tenantId);
    List<WorkflowComment> findByWorkflowIdAndTenantIdOrderByCreatedAtAsc(UUID workflowId, UUID tenantId);
}
