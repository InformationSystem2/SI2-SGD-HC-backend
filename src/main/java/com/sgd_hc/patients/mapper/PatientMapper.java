package com.sgd_hc.patients.mapper;

import org.springframework.stereotype.Component;
import java.util.Set;

import com.sgd_hc.patients.dto.PatientCreateDto;
import com.sgd_hc.patients.dto.PatientResponseDto;
import com.sgd_hc.patients.dto.PatientUpdateDto;
import com.sgd_hc.patients.entity.Gender;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.users.entity.DocumentType;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PatientMapper {

    private static final Set<String> ALL_READ_AUTHORITIES = Set.of(
            "patient:read:id",
            "patient:read:document_type",
            "patient:read:document_number",
            "patient:read:first_name",
            "patient:read:last_name",
            "patient:read:birth_date",
            "patient:read:gender",
            "patient:read:phone",
            "patient:read:address"
    );

    public Patient toEntity(PatientCreateDto dto) {
        if (dto.gender() == null || dto.gender().isBlank())
            throw new IllegalArgumentException("El género es requerido");
        Patient patient = new Patient();
        patient.setDocumentType(dto.documentType() != null ? DocumentType.valueOf(dto.documentType()) : DocumentType.CI);
        patient.setDocumentNumber(dto.documentNumber());
        patient.setFirstName(dto.firstName());
        patient.setLastName(dto.lastName());
        patient.setPhone(dto.phone());
        patient.setAddress(dto.address());
        patient.setGender(Gender.valueOf(dto.gender()));
        patient.setBirthDate(dto.birthDate());
        return patient;
    }

    public void updateEntityFromDto(PatientUpdateDto dto, Patient patient) {
        if (dto.firstName() != null) patient.setFirstName(dto.firstName());
        if (dto.lastName() != null) patient.setLastName(dto.lastName());
        if (dto.documentType() != null) patient.setDocumentType(DocumentType.valueOf(dto.documentType()));
        if (dto.documentNumber() != null) patient.setDocumentNumber(dto.documentNumber());
        if (dto.phone() != null) patient.setPhone(dto.phone());
        if (dto.address() != null) patient.setAddress(dto.address());
        if (dto.gender() != null) patient.setGender(Gender.valueOf(dto.gender()));
        if (dto.birthDate() != null) patient.setBirthDate(dto.birthDate());
    }

    public PatientResponseDto toResponseDto(Patient patient) {
        return toResponseDto(patient, ALL_READ_AUTHORITIES);
    }

    public PatientResponseDto toResponseDto(Patient patient, Set<String> userAuthorities) {
        return PatientResponseDto.builder()
                .id(userAuthorities.contains("patient:read:id") ? patient.getId() : null)
                .documentType(userAuthorities.contains("patient:read:document_type") && patient.getDocumentType() != null ? patient.getDocumentType().name() : null)
                .documentNumber(userAuthorities.contains("patient:read:document_number") ? patient.getDocumentNumber() : null)
                .firstName(userAuthorities.contains("patient:read:first_name") ? patient.getFirstName() : null)
                .lastName(userAuthorities.contains("patient:read:last_name") ? patient.getLastName() : null)
                .phone(userAuthorities.contains("patient:read:phone") ? patient.getPhone() : null)
                .address(userAuthorities.contains("patient:read:address") ? patient.getAddress() : null)
                .gender(userAuthorities.contains("patient:read:gender") && patient.getGender() != null ? patient.getGender().name() : null)
                .birthDate(userAuthorities.contains("patient:read:birth_date") ? patient.getBirthDate() : null)
                .build();
    }

    public Map<String, Object> toAuditMap(Patient entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId().toString());
        map.put("firstName", entity.getFirstName());
        map.put("lastName", entity.getLastName());
        map.put("documentType", entity.getDocumentType() != null ? entity.getDocumentType().name() : null);
        map.put("documentNumber", entity.getDocumentNumber());
        map.put("phone", entity.getPhone());
        map.put("address", entity.getAddress());
        map.put("gender", entity.getGender() != null ? entity.getGender().name() : null);
        map.put("birthDate", entity.getBirthDate() != null ? entity.getBirthDate().toString() : null);
        return map;
    }

    public Map<String, Object> toAuditMapFromDto(PatientResponseDto dto) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", dto.id().toString());
        map.put("firstName", dto.firstName());
        map.put("lastName", dto.lastName());
        map.put("documentType", dto.documentType());
        map.put("documentNumber", dto.documentNumber());
        map.put("phone", dto.phone());
        map.put("address", dto.address());
        map.put("gender", dto.gender());
        map.put("birthDate", dto.birthDate() != null ? dto.birthDate().toString() : null);
        return map;
    }    
}
