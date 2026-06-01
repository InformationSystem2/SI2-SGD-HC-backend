package com.sgd_hc.users.dto;

import java.util.Set;
import java.util.UUID;

public record UserCreateDto(
        String documentType,
        String documentNumber,
        String email,
        String firstName,
        String lastName,
        String password,
        String phone,
        String gender,
        Set<UUID> rolesIds
) {}
