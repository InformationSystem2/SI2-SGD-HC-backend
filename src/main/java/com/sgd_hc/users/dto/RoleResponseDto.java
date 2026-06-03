package com.sgd_hc.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;
import java.util.UUID;

import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoleResponseDto(
    Long id,
    String name,
    String description,
    Boolean isActive,
    Set<Long> permissionsIds
) {}
