package com.sgd_hc.users.dto;

import java.util.Set;
import java.util.UUID;

import lombok.Builder;

@Builder
public record RoleResponseDto(
    UUID id,
    String name,
    String description,
    Boolean isActive,
    Set<UUID> permissionsIds
) {}
