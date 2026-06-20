package com.sgd_hc.workflow.controller;

import com.sgd_hc.workflow.dto.CompleteTaskRequestDto;
import com.sgd_hc.workflow.dto.ReviewTaskResponseDto;
import com.sgd_hc.workflow.dto.StartReviewRequestDto;
import com.sgd_hc.workflow.service.ReviewTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/review-tasks")
@RequiredArgsConstructor
public class ReviewTaskController {

    private final ReviewTaskService reviewTaskService;

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('review-task:create')")
    public ResponseEntity<List<ReviewTaskResponseDto>> startReview(
            @Valid @RequestBody StartReviewRequestDto dto) {
        List<ReviewTaskResponseDto> tasks = reviewTaskService.startReview(
                dto.documentId(), dto.reviewerIds(), dto.priority(), dto.dueDate());
        return new ResponseEntity<>(tasks, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAuthority('review-task:update')")
    public ResponseEntity<ReviewTaskResponseDto> claimTask(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewTaskService.claimTask(id));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('review-task:update')")
    public ResponseEntity<ReviewTaskResponseDto> completeTask(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteTaskRequestDto dto) {
        return ResponseEntity.ok(reviewTaskService.completeTask(id, dto.outcome(), dto.comment()));
    }

    @GetMapping("/my-tasks")
    @PreAuthorize("hasAuthority('review-task:read')")
    public ResponseEntity<List<ReviewTaskResponseDto>> getMyTasks() {
        return ResponseEntity.ok(reviewTaskService.getMyTasks());
    }

    @GetMapping("/document/{docId}")
    @PreAuthorize("hasAuthority('review-task:read')")
    public ResponseEntity<List<ReviewTaskResponseDto>> getTasksByDocument(@PathVariable UUID docId) {
        return ResponseEntity.ok(reviewTaskService.getTasksByDocument(docId));
    }
}
