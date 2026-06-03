package com.sgd_hc.patients.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.sgd_hc.security.utils.SecurityUtils.*;
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
        Set<String> authorities = currentAuthorities();
        validateCreateAttributePermissions(dto, authorities);

        Patient patient = patientMapper.toEntity(dto);
        patient.setTenant(tenantResolverService.resolve());
        return patientMapper.toResponseDto(patientRepository.save(patient), authorities);
    }

    @Transactional(readOnly = true)
    public List<PatientResponseDto> getAllPatients() {
        Set<String> authorities = currentAuthorities();
        return patientRepository.findAll().stream()
                .map(patient -> patientMapper.toResponseDto(patient, authorities))
                .toList();
    }

    @Transactional(readOnly = true)
    public PatientResponseDto getPatientById(UUID id) {
        return patientMapper.toResponseDto(findOrThrow(id), currentAuthorities());
    }

    @Transactional
    public PatientResponseDto updatePatient(UUID id, PatientUpdateDto dto) {
        Set<String> authorities = currentAuthorities();
        validateUpdateAttributePermissions(dto, authorities);

        Patient patient = findOrThrow(id);
        patientMapper.updateEntityFromDto(dto, patient);
        return patientMapper.toResponseDto(patientRepository.save(patient), authorities);
    }

    @Transactional
    public void deletePatient(UUID id) {
        patientRepository.deleteById(id);
    }

    private Patient findOrThrow(UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found with id: " + id));
    }

    private void validateCreateAttributePermissions(PatientCreateDto dto, Set<String> authorities) {
        if (dto.documentType() != null) requireAuthority(authorities, "patient:create:document_type");
        if (dto.documentNumber() != null) requireAuthority(authorities, "patient:create:document_number");
        if (dto.gender() != null) requireAuthority(authorities, "patient:create:gender");
        if (dto.phone() != null) requireAuthority(authorities, "patient:create:phone");
        if (dto.address() != null) requireAuthority(authorities, "patient:create:address");
    }

    private void validateUpdateAttributePermissions(PatientUpdateDto dto, Set<String> authorities) {
        if (dto.firstName() != null) requireAuthority(authorities, "patient:update:first_name");
        if (dto.lastName() != null) requireAuthority(authorities, "patient:update:last_name");
        if (dto.documentType() != null) requireAuthority(authorities, "patient:update:document_type");
        if (dto.documentNumber() != null) requireAuthority(authorities, "patient:update:document_number");
        if (dto.phone() != null) requireAuthority(authorities, "patient:update:phone");
        if (dto.address() != null) requireAuthority(authorities, "patient:update:address");
        if (dto.gender() != null) requireAuthority(authorities, "patient:update:gender");
        if (dto.birthDate() != null) requireAuthority(authorities, "patient:update:birth_date");
    }

}
