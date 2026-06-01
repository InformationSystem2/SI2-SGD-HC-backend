package com.sgd_hc.users.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.dto.RoleCreateDto;
import com.sgd_hc.users.dto.RoleResponseDto;
import com.sgd_hc.users.dto.RoleUpdateDto;
import com.sgd_hc.users.entity.Permission;
import com.sgd_hc.users.entity.Role;
import com.sgd_hc.users.mapper.RoleMapper;
import com.sgd_hc.users.repository.PermissionRepository;
import com.sgd_hc.users.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository        roleRepository;
    private final PermissionRepository  permissionRepository;
    private final RoleMapper            roleMapper;
    private final TenantResolverService tenantResolverService;

    @Transactional
    public RoleResponseDto createRole(RoleCreateDto dto) {
        validateNameUniqueness(dto.name(), null);
        Set<Permission> permissions = fetchPermissions(dto.permissionsIds());
        Role role = roleMapper.toEntity(dto, permissions);
        role.setTenant(tenantResolverService.resolve());
        return roleMapper.toResponseDto(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public RoleResponseDto getRoleById(UUID id) {
        return roleMapper.toResponseDto(findRoleOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<RoleResponseDto> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(roleMapper::toResponseDto)
                .toList();
    }

    @Transactional
    public RoleResponseDto updateRole(UUID id, RoleUpdateDto dto) {
        Role existingRole = findRoleOrThrow(id);

        if (dto.name() != null)
            validateNameUniqueness(dto.name(), id);

        Set<Permission> permissions = dto.permissionsIds() != null
                ? fetchPermissions(dto.permissionsIds())
                : null;

        roleMapper.updateEntityFromDto(dto, existingRole, permissions);
        return roleMapper.toResponseDto(roleRepository.save(existingRole));
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = findRoleOrThrow(id);
        role.setIsActive(false);
        roleRepository.save(role);
    }

    private Role findRoleOrThrow(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found with id: " + id));
    }

    private void validateNameUniqueness(String name, UUID id) {
        roleRepository.findByName(name).ifPresent(r -> {
            if (id == null || !r.getId().equals(id))
                throw new IllegalArgumentException("Role name already exists: " + name);
        });
    }

    private Set<Permission> fetchPermissions(Set<UUID> ids) {
        if (ids == null || ids.isEmpty())
            return new HashSet<>();
        return new HashSet<>(permissionRepository.findAllById(ids));
    }
}
