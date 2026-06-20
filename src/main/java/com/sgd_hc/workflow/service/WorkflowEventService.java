package com.sgd_hc.workflow.service;

import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.workflow.dto.WorkflowEventResponseDto;
import com.sgd_hc.workflow.entity.ReviewTask;
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

    /**
     * Registra un evento en el historial visible del documento.
     * Diseñado para llamarse desde dentro de una transacción existente.
     */
    @Transactional
    public void recordEvent(Document doc, UUID tenantId, WorkflowEventType eventType,
                            User performedBy, ReviewTask reviewTask, Map<String, Object> details) {
        WorkflowEvent event = WorkflowEvent.builder()
                .tenantId(tenantId)
                .document(doc)
                .eventType(eventType)
                .performedBy(performedBy)
                .reviewTask(reviewTask)
                .performedAt(OffsetDateTime.now())
                .detailsJson(details)
                .build();

        workflowEventRepository.save(event);
        log.debug("WorkflowEvent registrado: docId={}, tipo={}", doc.getId(), eventType);
    }

    /**
     * Retorna el historial de eventos de un documento ordenado cronológicamente.
     * Resuelve el tenant desde el contexto de la request actual.
     */
    @Transactional(readOnly = true)
    public List<WorkflowEventResponseDto> getDocumentHistory(UUID documentId) {
        UUID tenantId = tenantResolverService.resolve().getId();
        return workflowEventRepository
                .findByDocumentIdAndTenantIdOrderByPerformedAtAsc(documentId, tenantId)
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
                event.getDetailsJson()
        );
    }
}
