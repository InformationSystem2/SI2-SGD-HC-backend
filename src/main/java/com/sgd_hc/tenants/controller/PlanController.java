package com.sgd_hc.tenants.controller;

import com.sgd_hc.tenants.dto.PlanDto;
import com.sgd_hc.tenants.dto.PlanUpdateDto;
import com.sgd_hc.tenants.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping
    public ResponseEntity<List<PlanDto>> getAllPlans() {
        return ResponseEntity.ok(planService.getAllActivePlans());
    }

    @GetMapping("/{name}")
    public ResponseEntity<PlanDto> getPlan(@PathVariable String name) {
        return ResponseEntity.ok(planService.getPlanByName(name));
    }

    @GetMapping("/{name}/limits")
    public ResponseEntity<Map<String, Long>> getPlanLimits(@PathVariable String name) {
        return ResponseEntity.ok(planService.getLimitsForPlan(name));
    }

    @GetMapping("/{name}/features")
    public ResponseEntity<Map<String, Boolean>> getPlanFeatures(@PathVariable String name) {
        return ResponseEntity.ok(planService.getFeaturesForPlan(name));
    }

    // ── ADMIN: Superuser-only plan management ─────────────────────────────────

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPERUSER')")
    public ResponseEntity<PlanDto> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody PlanUpdateDto dto) {
        return ResponseEntity.ok(planService.updatePlan(id, dto));
    }
}
