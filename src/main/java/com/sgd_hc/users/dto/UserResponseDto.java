package com.sgd_hc.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
        Set<Long> rolesIds
) {}
