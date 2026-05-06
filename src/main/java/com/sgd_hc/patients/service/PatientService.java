package com.sgd_hc.patients.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sgd_hc.patients.dto.PatientCreateDto;
import com.sgd_hc.patients.dto.PatientResponseDto;
import com.sgd_hc.patients.dto.PatientUpdateDto;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.patients.mapper.PatientMapper;
import com.sgd_hc.patients.repository.PatientRepository;
import com.sgd_hc.tenants.service.TenantResolverService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository    patientRepository;
    private final PatientMapper        patientMapper;
    private final TenantResolverService tenantResolverService;

    @Transactional
    public PatientResponseDto createPatient(PatientCreateDto dto) {
        Patient patient = patientMapper.toEntity(dto);
        patient.setTenant(tenantResolverService.resolve());
        return patientMapper.toResponseDto(patientRepository.save(patient));
    }

    @Transactional(readOnly = true)
    public List<PatientResponseDto> getAllPatients() {
        return patientRepository.findAll().stream()
                .map(patientMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PatientResponseDto getPatientById(UUID id) {
        return patientMapper.toResponseDto(findOrThrow(id));
    }

    @Transactional
    public PatientResponseDto updatePatient(UUID id, PatientUpdateDto dto) {
        Patient patient = findOrThrow(id);
        patientMapper.updateEntityFromDto(dto, patient);
        return patientMapper.toResponseDto(patientRepository.save(patient));
    }

    @Transactional
    public void deletePatient(UUID id) {
        patientRepository.deleteById(id);
    }

    private Patient findOrThrow(UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found with id: " + id));
    }
}
