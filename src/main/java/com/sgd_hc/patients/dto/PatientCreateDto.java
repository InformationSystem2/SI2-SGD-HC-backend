package com.sgd_hc.patients.dto;

import java.time.LocalDate;

public record PatientCreateDto(
        String documentType,
        String documentNumber,
        String firstName,
        String lastName,
        String phone,
        String address,
        String gender,
        LocalDate birthDate
) {}
