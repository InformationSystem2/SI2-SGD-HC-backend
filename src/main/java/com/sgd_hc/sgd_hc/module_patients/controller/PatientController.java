package com.sgd_hc.sgd_hc.module_patients.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sgd_hc.sgd_hc.module_patients.dto.PatientCreateDto;
import com.sgd_hc.sgd_hc.module_patients.dto.PatientResponseDto;
import com.sgd_hc.sgd_hc.module_patients.dto.PatientUpdateDto;
import com.sgd_hc.sgd_hc.module_patients.service.PatientService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/module_users/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    @PreAuthorize("hasAuthority('PATIENT_CREATE')")
    public ResponseEntity<PatientResponseDto> create(@RequestBody PatientCreateDto dto) {
        return new ResponseEntity<>(patientService.createPatient(dto), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    public ResponseEntity<List<PatientResponseDto>> getAll() {
        return ResponseEntity.ok(patientService.getAllPatients());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    public ResponseEntity<PatientResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(patientService.getPatientById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PATIENT_UPDATE')")
    public ResponseEntity<PatientResponseDto> update(@PathVariable Long id, @RequestBody PatientUpdateDto dto) {
        return ResponseEntity.ok(patientService.updatePatient(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PATIENT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patientService.deletePatient(id);
        return ResponseEntity.noContent().build();
    }
}
