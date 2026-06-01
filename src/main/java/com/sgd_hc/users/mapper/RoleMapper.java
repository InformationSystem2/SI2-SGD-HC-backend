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
        Set<UUID> permissionIds = entity.getPermissions() != null
                ? entity.getPermissions().stream()
                        .map(Permission::getId)
                        .collect(Collectors.toSet())
                : new HashSet<>();

        return RoleResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .permissionsIds(permissionIds)
                .build();
    }
}
