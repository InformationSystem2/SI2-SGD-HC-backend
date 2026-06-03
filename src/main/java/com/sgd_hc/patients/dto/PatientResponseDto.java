package com.sgd_hc.patients.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
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
