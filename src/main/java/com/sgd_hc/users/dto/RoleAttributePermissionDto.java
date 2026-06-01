package com.sgd_hc.users.dto;

import com.sgd_hc.users.entity.AccessLevel;
import lombok.Builder;

@Builder
public record RoleAttributePermissionDto(
    String entityName,
    String attributeName,
    AccessLevel accessLevel
) {}
