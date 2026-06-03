package com.sgd_hc.users.dto;

import java.util.UUID;

import lombok.Builder;

@Builder
public record PermissionResponseDto(
    Long id,
    String name,
    String module,
    String action,
    String description,
    Boolean isActive
) {}
