package com.sgd_hc.users.dto;

import java.util.Set;
import java.util.UUID;

public record UserUpdateDto(
        String documentType,
        String documentNumber,
        String firstName,
        String lastName,
        String password,
        String phone,
        Boolean isActive,
        Set<UUID> rolesIds
) {}
