package com.sgd_hc.security.dto;

import java.util.UUID;

public record ProfileResponseDto(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        String documentType,
        String documentNumber,
        String gender
) {}
