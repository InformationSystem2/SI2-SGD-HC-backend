package com.sgd_hc.patients.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;

@Builder
public record PatientResponseDto(
        UUID id,
        String documentType,
        String documentNumber,
        String firstName,
        String lastName,
        String phone,
        String address,
        String gender,
        LocalDate birthDate
) {}
