package com.sgd_hc.users.mapper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sgd_hc.users.dto.RoleCreateDto;
import com.sgd_hc.users.dto.RoleResponseDto;
import com.sgd_hc.users.dto.RoleUpdateDto;
import com.sgd_hc.users.dto.RoleAttributePermissionDto;
import com.sgd_hc.users.entity.Permission;
import com.sgd_hc.users.entity.Role;
import com.sgd_hc.users.entity.RoleAttributePermission;

@Component
public class RoleMapper {

    public Role toEntity(RoleCreateDto dto, Set<Permission> permissions) {
        Role role = Role.builder()
                .name(dto.name())
                .description(dto.description())
                .permissions(permissions != null ? permissions : new HashSet<>())
                .build();
                
        if (dto.attributePermissions() != null) {
            Set<RoleAttributePermission> attrPerms = dto.attributePermissions().stream()
                .map(attrDto -> RoleAttributePermission.builder()
                    .role(role)
                    .entityName(attrDto.entityName())
                    .attributeName(attrDto.attributeName())
                    .accessLevel(attrDto.accessLevel())
                    .build())
                .collect(Collectors.toSet());
            role.setAttributePermissions(attrPerms);
        }
        
        return role;
    }

    public void updateEntityFromDto(RoleUpdateDto dto, Role entity, Set<Permission> permissions) {
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.description() != null) entity.setDescription(dto.description());
        if (dto.isActive() != null) entity.setIsActive(dto.isActive());
        if (permissions != null) entity.setPermissions(permissions);
        
        if (dto.attributePermissions() != null) {
            Set<RoleAttributePermission> currentAttrPerms = entity.getAttributePermissions();
            
            java.util.Map<String, RoleAttributePermissionDto> dtoMap = dto.attributePermissions().stream()
                .collect(Collectors.toMap(
                    a -> a.entityName() + "_" + a.attributeName(),
                    a -> a
                ));
                
            currentAttrPerms.removeIf(existing -> {
                String key = existing.getEntityName() + "_" + existing.getAttributeName();
                RoleAttributePermissionDto dtoAttr = dtoMap.get(key);
                if (dtoAttr == null) {
                    return true;
                } else {
                    existing.setAccessLevel(dtoAttr.accessLevel());
                    dtoMap.remove(key);
                    return false;
                }
            });
            
            for (RoleAttributePermissionDto attrDto : dtoMap.values()) {
                currentAttrPerms.add(RoleAttributePermission.builder()
                    .role(entity)
                    .entityName(attrDto.entityName())
                    .attributeName(attrDto.attributeName())
                    .accessLevel(attrDto.accessLevel())
                    .build());
            }
        }
    }

    public RoleResponseDto toResponseDto(Role entity) {
        Set<UUID> permissionIds = entity.getPermissions() != null
                ? entity.getPermissions().stream()
                        .map(Permission::getId)
                        .collect(Collectors.toSet())
                : new HashSet<>();

        Set<RoleAttributePermissionDto> attrDtos = entity.getAttributePermissions() != null
                ? entity.getAttributePermissions().stream()
                        .map(attr -> RoleAttributePermissionDto.builder()
                            .entityName(attr.getEntityName())
                            .attributeName(attr.getAttributeName())
                            .accessLevel(attr.getAccessLevel())
                            .build())
                        .collect(Collectors.toSet())
                : new HashSet<>();

        return RoleResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .permissionsIds(permissionIds)
                .attributePermissions(attrDtos)
                .build();
    }
}
