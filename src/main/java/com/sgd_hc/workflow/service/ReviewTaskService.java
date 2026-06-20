package com.sgd_hc.workflow.service;

import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.entity.DocumentStatus;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.documents.service.DocumentVersioningService;
import com.sgd_hc.notifications.service.NotificationService;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.repository.UserRepository;
import com.sgd_hc.workflow.dto.ReviewTaskResponseDto;
import com.sgd_hc.workflow.entity.ReviewTask;
import com.sgd_hc.workflow.entity.ReviewTaskOutcome;
import com.sgd_hc.workflow.entity.ReviewTaskStatus;
import com.sgd_hc.workflow.entity.WorkflowEventType;
import com.sgd_hc.workflow.repository.ReviewTaskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewTaskService {

    private final ReviewTaskRepository       reviewTaskRepository;
    private final DocumentRepository         documentRepository;
    private final DocumentVersioningService  documentVersioningService;
    private final UserRepository             userRepository;
    private final WorkflowEventService       workflowEventService;
    private final WorkflowCommentService     workflowCommentService;
    private final NotificationService        notificationService;
    private final TenantResolverService      tenantResolverService;

    // ── startReview ──────────────────────────────────────────────────────────
    // Cambia el documento a PENDING_REVIEW, crea las tareas y notifica.
    // Todo ocurre en una sola transacción: si algo falla, todo se revierte.

    @Transactional
    public List<ReviewTaskResponseDto> startReview(UUID documentId, List<UUID> reviewerIds,
                                                   Integer priority, OffsetDateTime dueDate) {
        UUID tenantId = tenantResolverService.resolve().getId();
        User caller = currentUser();

        Document doc = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado: " + documentId));

        if (doc.getStatus() != DocumentStatus.DRAFT) {
            throw new IllegalStateException(
                    "Solo se puede iniciar revisión desde DRAFT. Estado actual: " + doc.getStatus());
        }

        // Cambiar estado del documento (con snapshot de versión)
        doc.setStatus(DocumentStatus.PENDING_REVIEW);
        documentVersioningService.recordVersion(doc, caller.getId(), "Enviado a revisión");

        // Evento: documento enviado a revisión
        workflowEventService.recordEvent(doc, tenantId, WorkflowEventType.SENT_TO_REVIEW, caller, null, null);

        List<ReviewTask> created = new ArrayList<>();
        for (UUID reviewerId : reviewerIds) {
            User reviewer = userRepository.findById(reviewerId)
                    .orElseThrow(() -> new EntityNotFoundException("Revisor no encontrado: " + reviewerId));

            ReviewTask task = ReviewTask.builder()
                    .tenantId(tenantId)
                    .document(doc)
                    .assignedTo(reviewer)
                    .status(ReviewTaskStatus.PENDING)
                    .priority(priority != null ? priority : 3)
                    .dueDate(dueDate)
                    .build();

            task = reviewTaskRepository.save(task);
            created.add(task);

            // Evento: tarea asignada
            workflowEventService.recordEvent(doc, tenantId, WorkflowEventType.TASK_ASSIGNED, caller, task,
                    Map.of("assignedTo", reviewer.getUsername()));

            // Notificar al revisor
            notificationService.notifyTaskAssigned(tenantId, reviewer, doc, task);

            log.info("ReviewTask creada: docId={}, reviewerId={}", documentId, reviewerId);
        }

        return created.stream().map(this::toDto).toList();
    }

    // ── claimTask ─────────────────────────────────────────────────────────────

    @Transactional
    public ReviewTaskResponseDto claimTask(UUID taskId) {
        UUID tenantId = tenantResolverService.resolve().getId();
        User claimer = currentUser();

        ReviewTask task = reviewTaskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tarea no encontrada: " + taskId));

        if (task.getStatus() != ReviewTaskStatus.PENDING) {
            throw new IllegalStateException(
                    "Solo se puede reclamar una tarea PENDING. Estado actual: " + task.getStatus());
        }

        task.setStatus(ReviewTaskStatus.IN_PROGRESS);
        task.setStartedAt(OffsetDateTime.now());
        task.setAssignedTo(claimer);
        reviewTaskRepository.save(task);

        workflowEventService.recordEvent(task.getDocument(), tenantId,
                WorkflowEventType.TASK_CLAIMED, claimer, task, null);

        return toDto(task);
    }

    // ── completeTask ──────────────────────────────────────────────────────────

    @Transactional
    public ReviewTaskResponseDto completeTask(UUID taskId, ReviewTaskOutcome outcome, String comment) {
        UUID tenantId = tenantResolverService.resolve().getId();
        User completer = currentUser();

        ReviewTask task = reviewTaskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tarea no encontrada: " + taskId));

        if (task.getStatus() == ReviewTaskStatus.COMPLETED
                || task.getStatus() == ReviewTaskStatus.CANCELLED) {
            throw new IllegalStateException("La tarea ya está " + task.getStatus());
        }

        task.setStatus(ReviewTaskStatus.COMPLETED);
        task.setOutcome(outcome);
        task.setCompletedAt(OffsetDateTime.now());
        task.setCompletedBy(completer);
        reviewTaskRepository.save(task);

        Document doc = task.getDocument();

        // Agregar comentario si se proporcionó
        if (comment != null && !comment.isBlank()) {
            workflowCommentService.addComment(doc, tenantId, completer, comment, task);
        }

        // Evento de la tarea
        WorkflowEventType taskEventType = outcome == ReviewTaskOutcome.APPROVED
                ? WorkflowEventType.TASK_APPROVED
                : WorkflowEventType.TASK_REJECTED;

        Map<String, Object> details = comment != null && !comment.isBlank()
                ? Map.of("comment", comment) : null;
        workflowEventService.recordEvent(doc, tenantId, taskEventType, completer, task, details);

        // Cambiar estado del documento (con snapshot de versión)
        DocumentStatus newDocStatus = outcome == ReviewTaskOutcome.APPROVED
                ? DocumentStatus.FINALIZED
                : DocumentStatus.REJECTED;

        String changeReason = outcome == ReviewTaskOutcome.APPROVED
                ? "Aprobado por " + completer.getFirstName()
                : "Rechazado: " + (comment != null ? comment : "sin motivo");

        doc.setStatus(newDocStatus);
        documentVersioningService.recordVersion(doc, completer.getId(), changeReason);

        // Evento del documento
        WorkflowEventType docEventType = outcome == ReviewTaskOutcome.APPROVED
                ? WorkflowEventType.DOCUMENT_FINALIZED
                : WorkflowEventType.DOCUMENT_REJECTED;
        workflowEventService.recordEvent(doc, tenantId, docEventType, completer, null, null);

        // Notificar al autor del documento
        notificationService.notifyTaskCompleted(tenantId, doc.getUploader(), doc, task);

        log.info("ReviewTask completada: taskId={}, outcome={}, docId={}",
                taskId, outcome, doc.getId());

        return toDto(task);
    }

    // ── cancelTasksForDocument ────────────────────────────────────────────────
    // Llamado al revertir a DRAFT (corrección por parte del autor).

    @Transactional
    public void cancelTasksForDocument(UUID documentId, UUID tenantId) {
        List<ReviewTask> active = reviewTaskRepository
                .findByDocumentIdAndTenantId(documentId, tenantId)
                .stream()
                .filter(t -> t.getStatus() == ReviewTaskStatus.PENDING
                        || t.getStatus() == ReviewTaskStatus.IN_PROGRESS)
                .toList();

        for (ReviewTask task : active) {
            task.setStatus(ReviewTaskStatus.CANCELLED);
            reviewTaskRepository.save(task);
            workflowEventService.recordEvent(task.getDocument(), tenantId,
                    WorkflowEventType.TASK_CANCELLED, null, task, null);
        }

        if (!active.isEmpty()) {
            log.info("Canceladas {} tarea(s) para el documento {}", active.size(), documentId);
        }
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ReviewTaskResponseDto> getMyTasks() {
        UUID tenantId = tenantResolverService.resolve().getId();
        User user = currentUser();
        return reviewTaskRepository
                .findByAssignedToIdAndTenantId(user.getId(), tenantId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewTaskResponseDto> getTasksByDocument(UUID documentId) {
        UUID tenantId = tenantResolverService.resolve().getId();
        return reviewTaskRepository
                .findByDocumentIdAndTenantId(documentId, tenantId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ── Escalamiento automático (cada hora) ───────────────────────────────────

    @Scheduled(fixedRate = 3_600_000)
    public void checkOverdueTasks() {
        // El contexto de tenant no está disponible en @Scheduled.
        // En una implementación completa se iteraría sobre tenants activos
        // y se activaría el TenantContext por cada uno.
        // Por ahora solo se registra el intento.
        log.debug("checkOverdueTasks ejecutado (sin contexto de tenant activo)");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReviewTaskResponseDto toDto(ReviewTask task) {
        User assignedTo = task.getAssignedTo();
        User completedBy = task.getCompletedBy();
        return new ReviewTaskResponseDto(
                task.getId(),
                task.getDocument().getId(),
                assignedTo.getId(),
                assignedTo.getFirstName() + " " + assignedTo.getLastName(),
                task.getStatus(),
                task.getOutcome(),
                task.getPriority(),
                task.getDueDate(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getCompletedAt(),
                completedBy != null ? completedBy.getId() : null,
                completedBy != null ? completedBy.getFirstName() + " " + completedBy.getLastName() : null
        );
    }

    private User currentUser() {
        Object principal = Objects.requireNonNull(
                SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        if (principal instanceof SecurityUser su) return su.getUser();
        throw new IllegalStateException("No se pudo determinar el usuario autenticado");
    }
}
