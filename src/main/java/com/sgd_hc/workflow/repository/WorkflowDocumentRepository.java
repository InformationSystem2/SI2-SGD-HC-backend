package com.sgd_hc.workflow.repository;

import com.sgd_hc.workflow.entity.WorkflowDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowDocumentRepository extends JpaRepository<WorkflowDocument, UUID> {

    List<WorkflowDocument> findByWorkflowId(UUID workflowId);

    List<WorkflowDocument> findByDocumentId(UUID documentId);

    boolean existsByWorkflowIdAndDocumentId(UUID workflowId, UUID documentId);

    long countByWorkflowId(UUID workflowId);
}
