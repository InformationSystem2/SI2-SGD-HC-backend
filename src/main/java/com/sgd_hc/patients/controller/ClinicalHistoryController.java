package com.sgd_hc.patients.controller;

import com.sgd_hc.patients.dto.ClinicalHistoryCreateDto;
import com.sgd_hc.patients.dto.ClinicalHistoryResponseDto;
import com.sgd_hc.patients.dto.ClinicalHistoryUpdateDto;
import com.sgd_hc.patients.service.ClinicalHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/module_users/clinical-histories")
@RequiredArgsConstructor
public class ClinicalHistoryController {

    private final ClinicalHistoryService clinicalHistoryService;

    @PostMapping
    @PreAuthorize("hasAuthority('patient:create')")
    public ResponseEntity<ClinicalHistoryResponseDto> create(@Valid @RequestBody ClinicalHistoryCreateDto dto) {
        return new ResponseEntity<>(clinicalHistoryService.create(dto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('patient:read')")
    public ResponseEntity<ClinicalHistoryResponseDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(clinicalHistoryService.getById(id));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAuthority('patient:read')")
    public ResponseEntity<ClinicalHistoryResponseDto> getByPatientId(@PathVariable UUID patientId) {
        return ResponseEntity.ok(clinicalHistoryService.getByPatientId(patientId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('patient:update')")
    public ResponseEntity<ClinicalHistoryResponseDto> update(@PathVariable UUID id, @Valid @RequestBody ClinicalHistoryUpdateDto dto) {
        return ResponseEntity.ok(clinicalHistoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('patient:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        clinicalHistoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
