package com.sgd_hc.users.dto;

import lombok.Builder;

@Builder
public record PermissionUpdateDto(
    String name,
    String description,
    Boolean isActive
) {}
