package com.sgd_hc.workflow.service;

import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.workflow.dto.WorkflowCommentResponseDto;
import com.sgd_hc.workflow.entity.ReviewTask;
import com.sgd_hc.workflow.entity.WorkflowComment;
import com.sgd_hc.workflow.repository.ReviewTaskRepository;
import com.sgd_hc.workflow.repository.WorkflowCommentRepository;
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
    private final TenantResolverService     tenantResolverService;

    /**
     * Agrega un comentario al hilo conversacional del documento.
     * Diseñado para llamarse desde dentro de una transacción existente.
     */
    @Transactional
    public WorkflowComment addComment(Document doc, UUID tenantId, User author,
                                      String commentText, ReviewTask reviewTask) {
        WorkflowComment comment = WorkflowComment.builder()
                .tenantId(tenantId)
                .document(doc)
                .author(author)
                .commentText(commentText)
                .reviewTask(reviewTask)
                .build();

        WorkflowComment saved = workflowCommentRepository.save(comment);
        log.debug("WorkflowComment agregado: docId={}, authorId={}", doc.getId(), author.getId());
        return saved;
    }

    /**
     * Versión pública para controllers: resuelve tenant y usuario desde el contexto HTTP.
     */
    @Transactional
    public WorkflowCommentResponseDto addCommentFromRequest(UUID documentId, String commentText, UUID reviewTaskId) {
        UUID tenantId = tenantResolverService.resolve().getId();
        User author = currentUser();

        Document doc = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado: " + documentId));

        ReviewTask task = null;
        if (reviewTaskId != null) {
            task = reviewTaskRepository.findByIdAndTenantId(reviewTaskId, tenantId).orElse(null);
        }

        WorkflowComment saved = addComment(doc, tenantId, author, commentText, task);
        return toDto(saved);
    }

    /**
     * Retorna los comentarios de un documento ordenados cronológicamente.
     * Resuelve el tenant desde el contexto de la request actual.
     */
    @Transactional(readOnly = true)
    public List<WorkflowCommentResponseDto> getDocumentComments(UUID documentId) {
        UUID tenantId = tenantResolverService.resolve().getId();
        return workflowCommentRepository
                .findByDocumentIdAndTenantIdOrderByCreatedAtAsc(documentId, tenantId)
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
