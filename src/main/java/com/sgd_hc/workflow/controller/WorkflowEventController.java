package com.sgd_hc.workflow.controller;

import com.sgd_hc.workflow.dto.WorkflowEventResponseDto;
import com.sgd_hc.workflow.service.WorkflowEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflow-events")
@RequiredArgsConstructor
public class WorkflowEventController {

    private final WorkflowEventService workflowEventService;

    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<WorkflowEventResponseDto>> getDocumentHistory(@PathVariable UUID documentId) {
        return ResponseEntity.ok(workflowEventService.getDocumentHistory(documentId));
    }

    @GetMapping("/workflow/{workflowId}")
    public ResponseEntity<List<WorkflowEventResponseDto>> getWorkflowHistory(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(workflowEventService.getWorkflowHistory(workflowId));
    }
}
