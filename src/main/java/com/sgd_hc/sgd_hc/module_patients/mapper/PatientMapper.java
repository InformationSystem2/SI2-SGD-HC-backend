package com.sgd_hc.sgd_hc.module_patients.mapper;

import org.springframework.stereotype.Component;

import com.sgd_hc.sgd_hc.module_patients.dto.PatientCreateDto;
import com.sgd_hc.sgd_hc.module_patients.dto.PatientResponseDto;
import com.sgd_hc.sgd_hc.module_patients.dto.PatientUpdateDto;
import com.sgd_hc.sgd_hc.module_patients.entity.Patient;

@Component
public class PatientMapper {

    public Patient toEntity(PatientCreateDto dto) {
        Patient patient = new Patient();
        patient.setEmail(dto.email());
        patient.setFirstName(dto.firstName());
        patient.setLastName(dto.lastName());
        patient.setDocumentType(dto.documentType());
        patient.setDocumentNumber(dto.documentNumber());
        patient.setPhone(dto.phone());
        patient.setGender(dto.gender() != null ? dto.gender() : "unknown");
        patient.setBirthDate(dto.birthDate());
        patient.setIsActive(true);
        return patient;
    }

    public void updateEntityFromDto(PatientUpdateDto dto, Patient patient) {
        patient.setFirstName(dto.firstName());
        patient.setLastName(dto.lastName());
        patient.setDocumentType(dto.documentType());
        patient.setDocumentNumber(dto.documentNumber());
        patient.setPhone(dto.phone());
        patient.setGender(dto.gender() != null ? dto.gender() : patient.getGender());
        if (dto.isActive() != null) patient.setIsActive(dto.isActive());
        if (dto.birthDate() != null) patient.setBirthDate(dto.birthDate());
    }

    public PatientResponseDto toResponseDto(Patient patient) {
        return PatientResponseDto.builder()
                .id(patient.getId())
                .username(patient.getUsername())
                .email(patient.getEmail())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .phone(patient.getPhone())
                .documentType(patient.getDocumentType())
                .documentNumber(patient.getDocumentNumber())
                .gender(patient.getGender())
                .isActive(patient.getIsActive())
                .birthDate(patient.getBirthDate())
                .build();
    }
}
