package com.sgd_hc.workflow.controller;

import com.sgd_hc.workflow.dto.AddCommentRequestDto;
import com.sgd_hc.workflow.dto.WorkflowCommentResponseDto;
import com.sgd_hc.workflow.dto.WorkflowEventResponseDto;
import com.sgd_hc.workflow.service.WorkflowCommentService;
import com.sgd_hc.workflow.service.WorkflowEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEventService   workflowEventService;
    private final WorkflowCommentService workflowCommentService;

    @GetMapping("/documents/{docId}/events")
    @PreAuthorize("hasAuthority('workflow:read')")
    public ResponseEntity<List<WorkflowEventResponseDto>> getDocumentHistory(
            @PathVariable UUID docId) {
        return ResponseEntity.ok(workflowEventService.getDocumentHistory(docId));
    }

    @GetMapping("/documents/{docId}/comments")
    @PreAuthorize("hasAuthority('workflow:read')")
    public ResponseEntity<List<WorkflowCommentResponseDto>> getDocumentComments(
            @PathVariable UUID docId) {
        return ResponseEntity.ok(workflowCommentService.getDocumentComments(docId));
    }

    @PostMapping("/documents/{docId}/comments")
    @PreAuthorize("hasAuthority('workflow:comment:create')")
    public ResponseEntity<WorkflowCommentResponseDto> addComment(
            @PathVariable UUID docId,
            @Valid @RequestBody AddCommentRequestDto dto) {
        WorkflowCommentResponseDto result = workflowCommentService.addCommentFromRequest(
                docId, dto.commentText(), dto.reviewTaskId());
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }
}
