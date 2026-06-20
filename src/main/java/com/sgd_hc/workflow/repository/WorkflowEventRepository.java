package com.sgd_hc.workflow.repository;

import com.sgd_hc.workflow.entity.WorkflowEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, UUID> {

    List<WorkflowEvent> findByDocumentIdAndTenantIdOrderByPerformedAtAsc(UUID documentId, UUID tenantId);
}
