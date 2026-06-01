package com.sgd_hc.tenants.dto;

import java.util.UUID;

public record AdminInfoDto(
        UUID userId,
        String username,
        String firstName,
        String lastName,
        String email,
        String phone
) {}