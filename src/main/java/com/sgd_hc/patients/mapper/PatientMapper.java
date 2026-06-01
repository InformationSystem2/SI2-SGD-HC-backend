package com.sgd_hc.patients.mapper;

import org.springframework.stereotype.Component;

import com.sgd_hc.patients.dto.PatientCreateDto;
import com.sgd_hc.patients.dto.PatientResponseDto;
import com.sgd_hc.patients.dto.PatientUpdateDto;
import com.sgd_hc.patients.entity.Gender;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.users.entity.DocumentType;

@Component
public class PatientMapper {

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
        return PatientResponseDto.builder()
                .id(patient.getId())
                .documentType(patient.getDocumentType() != null ? patient.getDocumentType().name() : null)
                .documentNumber(patient.getDocumentNumber())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .phone(patient.getPhone())
                .address(patient.getAddress())
                .gender(patient.getGender() != null ? patient.getGender().name() : null)
                .birthDate(patient.getBirthDate())
                .build();
    }
}
