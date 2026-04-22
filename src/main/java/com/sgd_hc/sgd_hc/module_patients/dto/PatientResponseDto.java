package com.sgd_hc.sgd_hc.module_patients.dto;

import java.time.LocalDate;

import lombok.Builder;

@Builder
public record PatientResponseDto(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        String documentType,
        String documentNumber,
        String gender,
        Boolean isActive,
        LocalDate birthDate
) {}
