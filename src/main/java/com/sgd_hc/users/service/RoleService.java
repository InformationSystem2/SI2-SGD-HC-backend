package com.sgd_hc.users.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.service.AuditableService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.sgd_hc.security.utils.SecurityUtils.*;
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
public class RoleService implements AuditableService<Long, Role> {

    private final RoleRepository        roleRepository;
    private final PermissionRepository  permissionRepository;
    private final RoleMapper            roleMapper;
    private final TenantResolverService tenantResolverService;

    @Transactional
    @Auditable(resourceType = "ROLE", actionType = ActionType.CREATE)
    public RoleResponseDto createRole(RoleCreateDto dto) {
        Set<String> authorities = currentAuthorities();
        validateCreateAttributePermissions(dto, authorities);

        validateNameUniqueness(dto.name(), null);
        Set<Permission> permissions = fetchPermissions(dto.permissionsIds());
        Role role = roleMapper.toEntity(dto, permissions);
        role.setTenant(tenantResolverService.resolve());
        return roleMapper.toResponseDto(roleRepository.save(role), authorities);
    }

    @Transactional(readOnly = true)
    public RoleResponseDto getRoleById(Long id) {
        return roleMapper.toResponseDto(findRoleOrThrow(id), currentAuthorities());
    }

    @Transactional(readOnly = true)
    public List<RoleResponseDto> getAllRoles() {
        Set<String> authorities = currentAuthorities();
        return roleRepository.findAll().stream()
                .map(role -> roleMapper.toResponseDto(role, authorities))
                .toList();
    }

    @Transactional
    @Auditable(resourceType = "ROLE", actionType = ActionType.UPDATE, idParamName = "id")
    public RoleResponseDto updateRole(Long id, RoleUpdateDto dto) {
        Set<String> authorities = currentAuthorities();
        validateUpdateAttributePermissions(dto, authorities);

        Role existingRole = findRoleOrThrow(id);

        if (dto.name() != null)
            validateNameUniqueness(dto.name(), id);

        Set<Permission> permissions = dto.permissionsIds() != null
                ? fetchPermissions(dto.permissionsIds())
                : null;

        roleMapper.updateEntityFromDto(dto, existingRole, permissions);
        return roleMapper.toResponseDto(roleRepository.save(existingRole), authorities);
    }

    @Transactional
    @Auditable(resourceType = "ROLE", actionType = ActionType.DELETE, idParamName = "id")
    public void deleteRole(Long id) {
        Role role = findRoleOrThrow(id);
        role.setIsActive(false);
        roleRepository.save(role);
    }

    private Role findRoleOrThrow(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found with id: " + id));
    }

    private void validateNameUniqueness(String name, Long id) {
        roleRepository.findByName(name).ifPresent(r -> {
            if (id == null || !r.getId().equals(id))
                throw new IllegalArgumentException("Role name already exists: " + name);
        });
    }

    private Set<Permission> fetchPermissions(Set<Long> ids) {
        if (ids == null || ids.isEmpty())
            return new HashSet<>();
        return new HashSet<>(permissionRepository.findAllById(ids));
    }

    private void validateCreateAttributePermissions(RoleCreateDto dto, Set<String> authorities) {
        if (dto.description() != null) requireAuthority(authorities, "role:create:description");
        if (dto.permissionsIds() != null) requireAuthority(authorities, "role:create:permissions");
    }

    private void validateUpdateAttributePermissions(RoleUpdateDto dto, Set<String> authorities) {
        if (dto.name() != null) requireAuthority(authorities, "role:update:name");
        if (dto.description() != null) requireAuthority(authorities, "role:update:description");
        if (dto.isActive() != null) requireAuthority(authorities, "role:update:is_active");
        if (dto.permissionsIds() != null) requireAuthority(authorities, "role:update:permissions");
    }

    @Override
    public Role getEntity(Long id) {
        return findRoleOrThrow(id);
    }

    @Override
    public Map<String, Object> toAuditMap(Role entity) {
        return roleMapper.toAuditMap(entity);
    }
}
