package com.sgd_hc.sgd_hc.module_patients.dto;

import java.time.LocalDate;

public record PatientUpdateDto(
        String firstName,
        String lastName,
        String documentType,
        String documentNumber,
        String phone,
        String gender,
        Boolean isActive,
        String password,
        LocalDate birthDate
) {}
