package com.sgd_hc.workflow.controller;

import com.sgd_hc.workflow.dto.WorkflowCommentResponseDto;
import com.sgd_hc.workflow.service.WorkflowCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow-comments")
@RequiredArgsConstructor
public class WorkflowCommentController {

    private final WorkflowCommentService workflowCommentService;

    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<WorkflowCommentResponseDto>> getDocumentComments(@PathVariable UUID documentId) {
        return ResponseEntity.ok(workflowCommentService.getDocumentComments(documentId));
    }

    @GetMapping("/workflow/{workflowId}")
    public ResponseEntity<List<WorkflowCommentResponseDto>> getWorkflowComments(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(workflowCommentService.getWorkflowComments(workflowId));
    }

    @PostMapping
    public ResponseEntity<WorkflowCommentResponseDto> addComment(
            @RequestBody Map<String, String> payload) {
        String documentIdStr = payload.get("documentId");
        UUID documentId = documentIdStr != null ? UUID.fromString(documentIdStr) : null;
        
        String workflowIdStr = payload.get("workflowId");
        UUID workflowId = workflowIdStr != null ? UUID.fromString(workflowIdStr) : null;
        
        String commentText = payload.get("commentText");
        String reviewTaskIdStr = payload.get("reviewTaskId");
        UUID reviewTaskId = reviewTaskIdStr != null ? UUID.fromString(reviewTaskIdStr) : null;
        return ResponseEntity.ok(workflowCommentService.addCommentFromRequest(documentId, workflowId, commentText, reviewTaskId));
    }
}
