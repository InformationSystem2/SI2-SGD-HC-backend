package com.sgd_hc.users.mapper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sgd_hc.users.dto.RoleCreateDto;
import com.sgd_hc.users.dto.RoleResponseDto;
import com.sgd_hc.users.dto.RoleUpdateDto;
import com.sgd_hc.users.entity.Permission;
import com.sgd_hc.users.entity.Role;

@Component
public class RoleMapper {

    private static final Set<String> ALL_READ_AUTHORITIES = Set.of(
            "role:read:id",
            "role:read:name",
            "role:read:description",
            "role:read:is_active",
            "role:read:permissions"
    );

    public Role toEntity(RoleCreateDto dto, Set<Permission> permissions) {
        return Role.builder()
                .name(dto.name())
                .description(dto.description())
                .permissions(permissions != null ? permissions : new HashSet<>())
                .build();
    }

    public void updateEntityFromDto(RoleUpdateDto dto, Role entity, Set<Permission> permissions) {
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.description() != null) entity.setDescription(dto.description());
        if (dto.isActive() != null) entity.setIsActive(dto.isActive());
        if (permissions != null) entity.setPermissions(permissions);
    }

    public RoleResponseDto toResponseDto(Role entity) {
        return toResponseDto(entity, ALL_READ_AUTHORITIES);
    }

    public RoleResponseDto toResponseDto(Role entity, Set<String> userAuthorities) {
        Set<Long> permissionIds = entity.getPermissions() != null
                ? entity.getPermissions().stream()
                        .map(Permission::getId)
                        .collect(Collectors.toSet())
                : new HashSet<>();

        return RoleResponseDto.builder()
                .id(userAuthorities.contains("role:read:id") ? entity.getId() : null)
                .name(userAuthorities.contains("role:read:name") ? entity.getName() : null)
                .description(userAuthorities.contains("role:read:description") ? entity.getDescription() : null)
                .isActive(userAuthorities.contains("role:read:is_active") ? entity.getIsActive() : null)
                .permissionsIds(userAuthorities.contains("role:read:permissions") ? permissionIds : null)
                .build();
    }
}
