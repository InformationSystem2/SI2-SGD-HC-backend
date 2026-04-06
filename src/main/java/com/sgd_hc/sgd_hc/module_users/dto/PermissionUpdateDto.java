package com.sgd_hc.sgd_hc.module_users.dto;

import lombok.Builder;

@Builder
public record PermissionUpdateDto(
    String name,
    String description,
    Boolean active
) {
}
