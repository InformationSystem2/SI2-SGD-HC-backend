package com.sgd_hc.workflow.controller;

import com.sgd_hc.workflow.entity.TaskDelegation;
import com.sgd_hc.workflow.service.DelegationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/delegations")
@RequiredArgsConstructor
public class DelegationController {

    private final DelegationService delegationService;

    @PostMapping
    @PreAuthorize("hasAuthority('review-task:update')")
    public ResponseEntity<TaskDelegation> createDelegation(
            @Valid @RequestBody CreateDelegationRequestDto dto) {
        TaskDelegation delegation = delegationService.createDelegation(
                dto.delegateId(), dto.startDate(), dto.endDate());
        return new ResponseEntity<>(delegation, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('review-task:read')")
    public ResponseEntity<List<TaskDelegation>> getActiveDelegations() {
        return ResponseEntity.ok(delegationService.getActiveDelegations());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('review-task:update')")
    public ResponseEntity<Void> cancelDelegation(@PathVariable UUID id) {
        delegationService.cancelDelegation(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateDelegationRequestDto(
            UUID delegateId,
            LocalDate startDate,
            LocalDate endDate
    ) {}
}
