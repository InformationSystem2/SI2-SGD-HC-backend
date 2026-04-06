package com.sgd_hc.sgd_hc.module_users.dto;

import java.util.Set;
import lombok.Builder;

@Builder
public record RoleResponseDto(
    Long id,
    String name,
    String description,
    Boolean active,
    Set<Long> permissionsIds
) {
}
