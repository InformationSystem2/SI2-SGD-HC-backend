package com.sgd_hc.patients.dto;

import java.time.LocalDate;

public record PatientUpdateDto(
        String firstName,
        String lastName,
        String documentType,
        String documentNumber,
        String phone,
        String address,
        String gender,
        LocalDate birthDate
) {}
