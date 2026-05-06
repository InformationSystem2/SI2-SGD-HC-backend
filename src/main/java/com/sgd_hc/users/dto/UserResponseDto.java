package com.sgd_hc.users.dto;

import java.util.Set;
import java.util.UUID;

public record UserResponseDto(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        String documentType,
        String documentNumber,
        String gender,
        Boolean isActive,
        Set<UUID> rolesIds
) {}
