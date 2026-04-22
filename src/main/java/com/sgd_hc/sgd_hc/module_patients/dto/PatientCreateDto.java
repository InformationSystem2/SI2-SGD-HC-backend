package com.sgd_hc.sgd_hc.module_patients.dto;

import java.time.LocalDate;

public record PatientCreateDto(
        String email,
        String firstName,
        String lastName,
        String password,
        String documentType,
        String documentNumber,
        String phone,
        String gender,
        LocalDate birthDate
) {}
