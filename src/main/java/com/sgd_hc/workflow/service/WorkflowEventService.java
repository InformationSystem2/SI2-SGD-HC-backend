package com.sgd_hc.workflow.service;

import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.workflow.dto.WorkflowEventResponseDto;
import com.sgd_hc.workflow.entity.ReviewTask;
import com.sgd_hc.workflow.entity.Workflow;
import com.sgd_hc.workflow.entity.WorkflowEvent;
import com.sgd_hc.workflow.entity.WorkflowEventType;
import com.sgd_hc.workflow.repository.WorkflowEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEventService {

    private final WorkflowEventRepository workflowEventRepository;
    private final TenantResolverService tenantResolverService;

    @Transactional
    public void recordEvent(Document doc, UUID tenantId, WorkflowEventType eventType,
                            User performedBy, ReviewTask reviewTask, Map<String, Object> details) {
        recordEvent(doc, tenantId, eventType, performedBy, reviewTask, details, null, null);
    }

    @Transactional
    public void recordEvent(Document doc, UUID tenantId, WorkflowEventType eventType,
                            User performedBy, ReviewTask reviewTask, Map<String, Object> details,
                            String result, String comment) {
        // Auto-resolve workflow from reviewTask if present
        Workflow resolvedWorkflow = (reviewTask != null && reviewTask.getWorkflow() != null)
                ? reviewTask.getWorkflow() : null;

        WorkflowEvent event = WorkflowEvent.builder()
                .tenantId(tenantId)
                .document(doc)
                .workflow(resolvedWorkflow)
                .eventType(eventType)
                .performedBy(performedBy)
                .reviewTask(reviewTask)
                .performedAt(OffsetDateTime.now())
                .detailsJson(details)
                .result(result)
                .comment(comment)
                .build();

        workflowEventRepository.save(event);
        log.debug("WorkflowEvent registrado: docId={}, workflowId={}, tipo={}",
                doc != null ? doc.getId() : null,
                resolvedWorkflow != null ? resolvedWorkflow.getId() : null,
                eventType);
    }

    @Transactional
    public void recordWorkflowEvent(Workflow workflow, UUID tenantId, WorkflowEventType eventType,
                                    User performedBy, String result, String comment) {
        WorkflowEvent event = WorkflowEvent.builder()
                .tenantId(tenantId)
                .workflow(workflow)
                .eventType(eventType)
                .performedBy(performedBy)
                .performedAt(OffsetDateTime.now())
                .result(result)
                .comment(comment)
                .build();

        workflowEventRepository.save(event);
        log.debug("WorkflowEvent registrado: workflowId={}, tipo={}", workflow.getId(), eventType);
    }

    @Transactional(readOnly = true)
    public List<WorkflowEventResponseDto> getDocumentHistory(UUID documentId) {
        UUID tenantId = tenantResolverService.resolve().getId();
        return workflowEventRepository
                .findByDocumentIdAndTenantIdOrderByPerformedAtAsc(documentId, tenantId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowEventResponseDto> getWorkflowHistory(UUID workflowId) {
        return workflowEventRepository
                .findByWorkflowIdOrderByPerformedAtAsc(workflowId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private WorkflowEventResponseDto toDto(WorkflowEvent event) {
        User performer = event.getPerformedBy();
        return new WorkflowEventResponseDto(
                event.getId(),
                event.getEventType(),
                performer != null ? performer.getId() : null,
                performer != null ? performer.getFirstName() + " " + performer.getLastName() : null,
                event.getPerformedAt(),
                event.getDetailsJson(),
                event.getResult(),
                event.getComment(),
                event.getDocument() != null ? event.getDocument().getId() : null,
                event.getDocument() != null ? 
                    (event.getDocument().getTemplate() != null ? event.getDocument().getTemplate().getName() : "Documento " + event.getDocument().getId().toString().substring(0, 8)) 
                    : null
        );
    }
}
