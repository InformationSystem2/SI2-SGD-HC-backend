package com.sgd_hc.workflow.service;

import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.entity.DocumentStatus;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.documents.service.DocumentVersioningService;
import com.sgd_hc.notifications.service.NotificationService;
import com.sgd_hc.security.config.tenant.TenantContext;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.tenants.service.PlanLimitValidator;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.repository.UserRepository;
import com.sgd_hc.documents.entity.DocumentVersion;
import com.sgd_hc.documents.repository.DocumentVersionRepository;
import com.sgd_hc.workflow.dto.WorkflowCreateRequestDto;
import com.sgd_hc.workflow.dto.WorkflowResponseDto;
import com.sgd_hc.workflow.entity.*;
import com.sgd_hc.workflow.repository.ReviewTaskRepository;
import com.sgd_hc.workflow.repository.WorkflowDocumentRepository;
import com.sgd_hc.workflow.repository.WorkflowRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowDocumentRepository workflowDocumentRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final UserRepository userRepository;
    private final DocumentVersioningService documentVersioningService;
    private final WorkflowEventService workflowEventService;
    private final NotificationService notificationService;
    private final TenantResolverService tenantResolverService;
    private final PlanLimitValidator planLimitValidator;

    @Transactional
    public WorkflowResponseDto createWorkflow(WorkflowCreateRequestDto request) {
        UUID tenantId = tenantResolverService.resolve().getId();
        User creator = currentUser();

        planLimitValidator.checkActiveReviewTasksLimit(tenantId);
        planLimitValidator.checkReviewTasksMonthlyLimit(tenantId);

        User assignee = userRepository.findById(request.assigneeId())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + request.assigneeId()));

        String title = request.title() != null && !request.title().isBlank()
                ? request.title()
                : "Flujo de trabajo - " + java.time.LocalDate.now();

        Workflow workflow = Workflow.builder()
                .tenantId(tenantId)
                .title(title)
                .message(request.message())
                .creator(creator)
                .assignee(assignee)
                .priority(request.priority() != null ? request.priority() : 3)
                .dueDate(request.dueDate())
                .sendEmailNotifications(Boolean.TRUE.equals(request.sendEmailNotifications()))
                .status(WorkflowStatus.ACTIVE)
                .build();
        workflow = workflowRepository.save(workflow);

        workflowEventService.recordWorkflowEvent(workflow, tenantId,
                WorkflowEventType.DOCUMENT_CREATED, creator, "CREATED", null);

        Set<UUID> allDocumentIds = new LinkedHashSet<>();
        if (request.documentIds() != null) {
            allDocumentIds.addAll(request.documentIds());
        }
        if (request.taskAssignments() != null) {
            for (var assignment : request.taskAssignments()) {
                allDocumentIds.add(assignment.documentId());
            }
        }

        for (UUID docId : allDocumentIds) {
            Document doc = documentRepository.findByIdAndTenantId(docId, tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado: " + docId));

            WorkflowDocument wd = WorkflowDocument.builder()
                    .workflow(workflow)
                    .document(doc)
                    .build();
            workflowDocumentRepository.save(wd);

            doc.setStatus(DocumentStatus.PENDING_REVIEW);
            documentVersioningService.recordVersion(doc, creator.getId(), "Enviado a revisión");

            List<UUID> reviewerIds = resolveReviewerIds(request, docId, assignee);

            for (UUID reviewerId : reviewerIds) {
                if (reviewerId.equals(creator.getId())) {
                    throw new IllegalArgumentException("No puedes asignarte tareas de revisión a ti mismo.");
                }
                User reviewer = userRepository.findById(reviewerId)
                        .orElseThrow(() -> new EntityNotFoundException("Revisor no encontrado: " + reviewerId));

                ReviewTask task = ReviewTask.builder()
                        .tenantId(tenantId)
                        .workflow(workflow)
                        .document(doc)
                        .assignedTo(reviewer)
                        .documentVersion(doc.getVersionNumber())
                        .status(ReviewTaskStatus.PENDING)
                        .priority(workflow.getPriority())
                        .dueDate(workflow.getDueDate())
                        .build();
                task = reviewTaskRepository.save(task);

                workflowEventService.recordEvent(doc, tenantId,
                        WorkflowEventType.TASK_ASSIGNED, creator, task,
                        Map.of("assignedTo", reviewer.getUsername()));

                notificationService.notifyTaskAssigned(tenantId, reviewer, doc, task);
            }
        }

        log.info("Workflow creado: workflowId={}, docs={}, assignee={}",
                workflow.getId(), allDocumentIds.size(), assignee.getId());

        return getWorkflowDetails(workflow.getId());
    }

    @Transactional(readOnly = true)
    public WorkflowResponseDto getWorkflowDetails(UUID workflowId) {
        UUID tenantId = tenantResolverService.resolve().getId();

        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new EntityNotFoundException("Workflow no encontrado: " + workflowId));

        List<WorkflowDocument> workflowDocs = workflowDocumentRepository.findByWorkflowId(workflowId);
        List<ReviewTask> tasks = reviewTaskRepository.findByWorkflowIdOrderByCreatedAtAsc(workflowId);

        List<WorkflowResponseDto.WorkflowDocumentDto> docDtos = workflowDocs.stream()
                .map(wd -> {
                    Document doc = wd.getDocument();
                    return new WorkflowResponseDto.WorkflowDocumentDto(
                            wd.getId(),
                            doc.getId(),
                            doc.getTemplate() != null ? doc.getTemplate().getName() : "Documento " + doc.getId().toString().substring(0, 8),
                            doc.getStatus().name(),
                            wd.getAddedAt()
                    );
                })
                .toList();

        List<WorkflowResponseDto.WorkflowTaskDto> taskDtos = tasks.stream()
                .map(this::toTaskDto)
                .toList();

        long pendingCount = tasks.stream()
                .filter(t -> t.getStatus() == ReviewTaskStatus.PENDING || t.getStatus() == ReviewTaskStatus.IN_PROGRESS)
                .count();
        long completedCount = tasks.stream()
                .filter(t -> t.getStatus() == ReviewTaskStatus.COMPLETED)
                .count();

        User creator = workflow.getCreator();
        User assignee = workflow.getAssignee();

        return new WorkflowResponseDto(
                workflow.getId(),
                workflow.getTitle(),
                workflow.getMessage(),
                creator.getId(),
                creator.getFirstName() + " " + creator.getLastName(),
                creator.getUsername(),
                assignee.getId(),
                assignee.getFirstName() + " " + assignee.getLastName(),
                workflow.getStatus(),
                workflow.getPriority(),
                workflow.getDueDate(),
                workflow.getSendEmailNotifications(),
                workflow.getCreatedAt(),
                workflow.getUpdatedAt(),
                docDtos,
                taskDtos,
                pendingCount,
                completedCount
        );
    }

    @Transactional
    public void cancelWorkflow(UUID workflowId) {
        UUID tenantId = tenantResolverService.resolve().getId();
        User caller = currentUser();

        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new EntityNotFoundException("Workflow no encontrado: " + workflowId));

        if (workflow.getStatus() != WorkflowStatus.ACTIVE) {
            throw new IllegalStateException("Solo se pueden cancelar workflows activos. Estado actual: " + workflow.getStatus());
        }

        if (!workflow.getCreator().getId().equals(caller.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Solo el autor del workflow puede cancelarlo");
        }

        workflow.setStatus(WorkflowStatus.CANCELLED);
        workflowRepository.save(workflow);

        List<ReviewTask> activeTasks = reviewTaskRepository.findByWorkflowIdOrderByCreatedAtAsc(workflowId)
                .stream()
                .filter(t -> t.getStatus() == ReviewTaskStatus.PENDING || t.getStatus() == ReviewTaskStatus.IN_PROGRESS)
                .toList();

        for (ReviewTask task : activeTasks) {
            task.setStatus(ReviewTaskStatus.CANCELLED);
            reviewTaskRepository.save(task);

            workflowEventService.recordEvent(task.getDocument(), tenantId,
                    WorkflowEventType.TASK_CANCELLED, caller, task, null);
        }

        for (WorkflowDocument wd : workflowDocumentRepository.findByWorkflowId(workflowId)) {
            Document doc = wd.getDocument();
            if (doc.getStatus() == DocumentStatus.PENDING_REVIEW) {
                doc.setStatus(DocumentStatus.DRAFT);
                documentVersioningService.recordVersion(doc, caller.getId(), "Workflow cancelado, revertido a DRAFT");
            }
        }

        workflowEventService.recordWorkflowEvent(workflow, tenantId,
                WorkflowEventType.TASK_CANCELLED, caller, "CANCELLED", null);

        log.info("Workflow cancelado: workflowId={}, tasks={}", workflowId, activeTasks.size());
    }

    @Transactional
    public WorkflowResponseDto resubmitDocument(UUID workflowId, UUID documentId, UUID newReviewerId, Integer selectedVersionNumber) {
        UUID tenantId = tenantResolverService.resolve().getId();
        User caller = currentUser();

        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new EntityNotFoundException("Workflow no encontrado: " + workflowId));

        if (workflow.getStatus() != WorkflowStatus.ACTIVE) {
            throw new IllegalStateException("Solo se pueden corregir documentos en workflows activos. Estado actual: " + workflow.getStatus());
        }

        Document doc = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado: " + documentId));

        if (doc.getStatus() != DocumentStatus.REJECTED && doc.getStatus() != DocumentStatus.DRAFT) {
            throw new IllegalStateException("Solo se pueden corregir documentos rechazados o editados (borrador). Estado actual: " + doc.getStatus());
        }

        // Find previous tasks for this document in this workflow
        List<ReviewTask> previousTasks = reviewTaskRepository.findByWorkflowIdAndDocumentId(workflowId, documentId);
        
        // Find the rejected version
        Integer rejectedVersion = previousTasks.stream()
                .filter(t -> t.getStatus() == ReviewTaskStatus.COMPLETED && t.getOutcome() == ReviewTaskOutcome.REJECTED)
                .map(ReviewTask::getDocumentVersion)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);

        if (rejectedVersion != null && rejectedVersion.equals(selectedVersionNumber)) {
            throw new IllegalArgumentException("No puedes reenviar la misma versión que fue rechazada (Versión " + rejectedVersion + ").");
        }

        if (selectedVersionNumber != null && selectedVersionNumber < doc.getVersionNumber()) {
            // User selected an older historical version, restore its content
            DocumentVersion oldVersion = documentVersionRepository
                    .findByDocumentIdAndTenantIdOrderByVersionNumberDesc(documentId, tenantId).stream()
                    .filter(v -> v.getVersionNumber().equals(selectedVersionNumber))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Versión histórica no encontrada: " + selectedVersionNumber));

            doc.setClinicalContent(oldVersion.getClinicalContent());
            doc.setFileUrl(oldVersion.getFileUrl());
            // Create a snapshot to represent the restored version
            documentVersioningService.recordVersion(doc, caller.getId(), "Restaurada versión histórica " + selectedVersionNumber);
        }

        // Revert document to PENDING_REVIEW
        doc.setStatus(DocumentStatus.PENDING_REVIEW);
        documentVersioningService.recordVersion(doc, caller.getId(), "Corregido y reenviado a revisión");

        // Record corrected event
        workflowEventService.recordWorkflowEvent(workflow, tenantId,
                WorkflowEventType.DOCUMENT_CORRECTED, caller, "CORRECTED", null);

        Set<UUID> reviewerIds = new HashSet<>();
        if (newReviewerId != null) {
            reviewerIds.add(newReviewerId);
        } else {
            // Find previous reviewers for this document in this workflow
            reviewerIds = previousTasks.stream()
                    .map(t -> t.getAssignedTo().getId())
                    .collect(Collectors.toSet());

            if (reviewerIds.isEmpty()) {
                // Fallback: assign to the workflow assignee
                reviewerIds.add(workflow.getAssignee().getId());
            }
        }

        // Create new tasks for the same reviewers
        for (UUID reviewerId : reviewerIds) {
            if (reviewerId.equals(caller.getId())) {
                throw new IllegalArgumentException("No puedes asignarte tareas de revisión a ti mismo.");
            }
            User reviewer = userRepository.findById(reviewerId)
                    .orElseThrow(() -> new EntityNotFoundException("Revisor no encontrado: " + reviewerId));

            ReviewTask newTask = ReviewTask.builder()
                    .tenantId(tenantId)
                    .workflow(workflow)
                    .document(doc)
                    .assignedTo(reviewer)
                    .documentVersion(doc.getVersionNumber())
                    .status(ReviewTaskStatus.PENDING)
                    .priority(workflow.getPriority())
                    .dueDate(workflow.getDueDate())
                    .build();
            newTask = reviewTaskRepository.save(newTask);

            workflowEventService.recordEvent(doc, tenantId,
                    WorkflowEventType.TASK_ASSIGNED, caller, newTask,
                    Map.of("assignedTo", reviewer.getUsername()));

            notificationService.notifyTaskAssigned(tenantId, reviewer, doc, newTask);
        }

        log.info("Documento reenviado a revisión: workflowId={}, docId={}, reviewers={}",
                workflowId, documentId, reviewerIds.size());

        return getWorkflowDetails(workflowId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponseDto> getMyWorkflows(WorkflowStatus status) {
        UUID tenantId = tenantResolverService.resolve().getId();
        User user = currentUser();

        List<Workflow> workflows;
        if (status != null) {
            workflows = workflowRepository.findByCreatorIdAndTenantIdOrderByCreatedAtDesc(user.getId(), tenantId)
                .stream().filter(w -> w.getStatus() == status).toList();
        } else {
            workflows = workflowRepository.findByCreatorIdAndTenantIdOrderByCreatedAtDesc(user.getId(), tenantId);
        }

        return workflows.stream()
                .map(w -> getWorkflowDetails(w.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponseDto> getWorkflowsAssignedToMe(WorkflowStatus status) {
        UUID tenantId = tenantResolverService.resolve().getId();
        User user = currentUser();

        List<Workflow> workflows;
        if (status != null) {
            workflows = workflowRepository.findByAssigneeIdAndTenantIdOrderByCreatedAtDesc(user.getId(), tenantId)
                .stream().filter(w -> w.getStatus() == status).toList();
        } else {
            workflows = workflowRepository.findByAssigneeIdAndTenantIdOrderByCreatedAtDesc(user.getId(), tenantId);
        }

        return workflows.stream()
                .map(w -> getWorkflowDetails(w.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWorkflowStats() {
        UUID tenantId = tenantResolverService.resolve().getId();
        User user = currentUser();

        long activeCount = workflowRepository.countByCreatorIdAndTenantIdAndStatus(
                user.getId(), tenantId, WorkflowStatus.ACTIVE);
        long completedCount = workflowRepository.countByCreatorIdAndTenantIdAndStatus(
                user.getId(), tenantId, WorkflowStatus.COMPLETED);
        long cancelledCount = workflowRepository.countByCreatorIdAndTenantIdAndStatus(
                user.getId(), tenantId, WorkflowStatus.CANCELLED);

        return Map.of(
                "activeCount", activeCount,
                "completedCount", completedCount,
                "cancelledCount", cancelledCount
        );
    }

    private List<UUID> resolveReviewerIds(WorkflowCreateRequestDto request, UUID documentId, User defaultAssignee) {
        if (request.taskAssignments() != null) {
            for (var assignment : request.taskAssignments()) {
                if (assignment.documentId().equals(documentId) && assignment.reviewerIds() != null) {
                    return assignment.reviewerIds();
                }
            }
        }
        return List.of(defaultAssignee.getId());
    }

    private WorkflowResponseDto.WorkflowTaskDto toTaskDto(ReviewTask task) {
        User assignedTo = task.getAssignedTo();
        User completedBy = task.getCompletedBy();
        Document doc = task.getDocument();
        return new WorkflowResponseDto.WorkflowTaskDto(
                task.getId(),
                doc.getId(),
                doc.getTemplate() != null ? doc.getTemplate().getName() : "Documento " + doc.getId().toString().substring(0, 8),
                assignedTo.getId(),
                assignedTo.getFirstName() + " " + assignedTo.getLastName(),
                task.getDocumentVersion(),
                task.getStatus(),
                task.getOutcome(),
                task.getPriority(),
                task.getDueDate(),
                task.getCreatedAt(),
                task.getCompletedAt(),
                completedBy != null ? completedBy.getId() : null,
                completedBy != null ? completedBy.getFirstName() + " " + completedBy.getLastName() : null
        );
    }

    private User currentUser() {
        Object principal = Objects.requireNonNull(
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication())
                .getPrincipal();
        if (principal instanceof SecurityUser su) return su.getUser();
        throw new IllegalStateException("No se pudo determinar el usuario autenticado");
    }
}
