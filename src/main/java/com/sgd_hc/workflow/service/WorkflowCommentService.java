package com.sgd_hc.workflow.service;

import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.workflow.dto.WorkflowCommentResponseDto;
import com.sgd_hc.workflow.entity.ReviewTask;
import com.sgd_hc.workflow.entity.Workflow;
import com.sgd_hc.workflow.entity.WorkflowComment;
import com.sgd_hc.workflow.entity.WorkflowEventType;
import com.sgd_hc.workflow.repository.ReviewTaskRepository;
import com.sgd_hc.workflow.repository.WorkflowCommentRepository;
import com.sgd_hc.workflow.repository.WorkflowRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowCommentService {

    private final WorkflowCommentRepository workflowCommentRepository;
    private final DocumentRepository        documentRepository;
    private final ReviewTaskRepository      reviewTaskRepository;
    private final WorkflowRepository        workflowRepository;
    private final TenantResolverService     tenantResolverService;
    private final WorkflowEventService      workflowEventService;

    @Transactional
    public WorkflowComment addComment(Document doc, Workflow workflow, UUID tenantId, User author,
                                      String commentText, ReviewTask reviewTask) {
        WorkflowComment comment = WorkflowComment.builder()
                .tenantId(tenantId)
                .document(doc)
                .workflow(workflow)
                .author(author)
                .commentText(commentText)
                .reviewTask(reviewTask)
                .build();

        WorkflowComment saved = workflowCommentRepository.save(comment);
        
        java.util.Map<String, Object> details = java.util.Map.of("comment", commentText);
        if (doc != null) {
            workflowEventService.recordEvent(doc, tenantId, WorkflowEventType.COMMENT_ADDED, author, reviewTask, details);
        } else if (workflow != null) {
            workflowEventService.recordWorkflowEvent(workflow, tenantId, WorkflowEventType.COMMENT_ADDED, author, null, commentText);
        }
        
        log.debug("WorkflowComment agregado: authorId={}", author.getId());
        return saved;
    }

    @Transactional
    public WorkflowCommentResponseDto addCommentFromRequest(UUID documentId, UUID workflowId, String commentText, UUID reviewTaskId) {
        UUID tenantId = tenantResolverService.resolve().getId();
        User author = currentUser();

        Document doc = null;
        if (documentId != null) {
            doc = documentRepository.findByIdAndTenantId(documentId, tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado: " + documentId));
        }

        Workflow workflow = null;
        if (workflowId != null) {
            workflow = workflowRepository.findByIdAndTenantId(workflowId, tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Workflow no encontrado: " + workflowId));
        }
        
        if (doc == null && workflow == null) {
            throw new IllegalArgumentException("Debe proveer documentId o workflowId");
        }

        ReviewTask task = null;
        if (reviewTaskId != null) {
            task = reviewTaskRepository.findByIdAndTenantId(reviewTaskId, tenantId).orElse(null);
        }

        WorkflowComment saved = addComment(doc, workflow, tenantId, author, commentText, task);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<WorkflowCommentResponseDto> getDocumentComments(UUID documentId) {
        UUID tenantId = tenantResolverService.resolve().getId();
        return workflowCommentRepository
                .findByDocumentIdAndTenantIdOrderByCreatedAtAsc(documentId, tenantId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowCommentResponseDto> getWorkflowComments(UUID workflowId) {
        UUID tenantId = tenantResolverService.resolve().getId();
        return workflowCommentRepository
                .findByWorkflowIdAndTenantIdOrderByCreatedAtAsc(workflowId, tenantId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private User currentUser() {
        Object principal = Objects.requireNonNull(
                SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        if (principal instanceof SecurityUser su) return su.getUser();
        throw new IllegalStateException("No se pudo determinar el usuario autenticado");
    }

    private WorkflowCommentResponseDto toDto(WorkflowComment comment) {
        User author = comment.getAuthor();
        ReviewTask task = comment.getReviewTask();
        return new WorkflowCommentResponseDto(
                comment.getId(),
                author.getId(),
                author.getFirstName() + " " + author.getLastName(),
                task != null ? task.getId() : null,
                comment.getCommentText(),
                comment.getCreatedAt()
        );
    }
}
