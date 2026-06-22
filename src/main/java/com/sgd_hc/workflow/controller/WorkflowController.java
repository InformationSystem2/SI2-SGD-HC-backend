package com.sgd_hc.workflow.controller;

import com.sgd_hc.workflow.dto.WorkflowCreateRequestDto;
import com.sgd_hc.workflow.dto.WorkflowResponseDto;
import com.sgd_hc.workflow.entity.WorkflowStatus;
import com.sgd_hc.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping
    @PreAuthorize("hasAuthority('workflow:create')")
    public ResponseEntity<WorkflowResponseDto> createWorkflow(
            @Valid @RequestBody WorkflowCreateRequestDto request) {
        WorkflowResponseDto created = workflowService.createWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:read')")
    public ResponseEntity<WorkflowResponseDto> getWorkflow(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getWorkflowDetails(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('workflow:cancel')")
    public ResponseEntity<Map<String, String>> cancelWorkflow(@PathVariable UUID id) {
        workflowService.cancelWorkflow(id);
        return ResponseEntity.ok(Map.of("message", "Workflow cancelado exitosamente"));
    }

    @PostMapping("/{id}/documents/{docId}/resubmit")
    @PreAuthorize("hasAuthority('workflow:update')")
    public ResponseEntity<WorkflowResponseDto> resubmitDocument(
            @PathVariable UUID id, @PathVariable UUID docId,
            @RequestParam(required = false) UUID reviewerId,
            @RequestParam Integer selectedVersion) {
        WorkflowResponseDto updated = workflowService.resubmitDocument(id, docId, reviewerId, selectedVersion);
        return ResponseEntity.ok(updated);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('workflow:read')")
    public ResponseEntity<List<WorkflowResponseDto>> getMyWorkflows(
            @RequestParam(required = false) WorkflowStatus status) {
        return ResponseEntity.ok(workflowService.getMyWorkflows(status));
    }

    @GetMapping("/assigned-to-me")
    @PreAuthorize("hasAuthority('workflow:read')")
    public ResponseEntity<List<WorkflowResponseDto>> getWorkflowsAssignedToMe(
            @RequestParam(required = false) WorkflowStatus status) {
        return ResponseEntity.ok(workflowService.getWorkflowsAssignedToMe(status));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('workflow:read')")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(workflowService.getWorkflowStats());
    }
}
