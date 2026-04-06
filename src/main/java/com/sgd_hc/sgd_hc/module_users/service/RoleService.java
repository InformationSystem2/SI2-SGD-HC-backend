package com.sgd_hc.sgd_hc.module_users.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sgd_hc.sgd_hc.module_users.dto.RoleCreateDto;
import com.sgd_hc.sgd_hc.module_users.dto.RoleResponseDto;
import com.sgd_hc.sgd_hc.module_users.dto.RoleUpdateDto;
import com.sgd_hc.sgd_hc.module_users.entity.Permission;
import com.sgd_hc.sgd_hc.module_users.entity.Role;
import com.sgd_hc.sgd_hc.module_users.mapper.RoleMapper;
import com.sgd_hc.sgd_hc.module_users.repository.PermissionRepository;
import com.sgd_hc.sgd_hc.module_users.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Servicio encargado de gestionar la lógica de negocio para la entidad Role.
 * Adoptando el patrón de "Helpers" para un código más limpio y mantenible.
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;

    /**
     * Crea un nuevo rol.
     */
    @Transactional
    public RoleResponseDto createRole(RoleCreateDto dto) {
        validateNameUniqueness(dto.name(), null);
        
        Set<Permission> permissions = fetchPermissions(dto.permissionsIds());
        Role role = roleMapper.toEntity(dto, permissions);
        return roleMapper.toResponseDto(roleRepository.save(role));
    }

    /**
     * Busca un rol por su ID.
     */
    @Transactional(readOnly = true)
    public RoleResponseDto getRoleById(Long id) {
        return roleMapper.toResponseDto(findRoleOrThrow(id));
    }

    /**
     * Recupera todos los roles.
     */
    @Transactional(readOnly = true)
    public List<RoleResponseDto> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(roleMapper::toResponseDto)
                .toList();
    }

    /**
     * Actualiza un rol existente.
     */
    @Transactional
    public RoleResponseDto updateRole(Long id, RoleUpdateDto dto) {
        Role existingRole = findRoleOrThrow(id);
        
        if (dto.name() != null) {
            validateNameUniqueness(dto.name(), id);
        }

        Set<Permission> permissions = (dto.permissionsIds() != null) 
                ? fetchPermissions(dto.permissionsIds()) 
                : null;
        
        roleMapper.updateEntityFromDto(dto, existingRole, permissions);
        return roleMapper.toResponseDto(roleRepository.save(existingRole));
    }

    /**
     * Borrado lógico de un rol.
     */
    @Transactional
    public void deleteRole(Long id) {
        Role role = findRoleOrThrow(id);
        role.setActive(false);
        roleRepository.save(role);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Busca un rol por ID o lanza una excepción si no existe.
     */
    private Role findRoleOrThrow(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found with id: " + id));
    }

    /**
     * Valida que el nombre sea único, ignorando el ID actual en caso de actualización.
     */
    private void validateNameUniqueness(String name, Long id) {
        roleRepository.findByName(name).ifPresent(r -> {
            if (id == null || !r.getId().equals(id)) {
                throw new IllegalArgumentException("Role name already exists: " + name);
            }
        });
    }

    /**
     * Recupera un conjunto de permisos a partir de sus IDs.
     */
    private Set<Permission> fetchPermissions(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(permissionRepository.findAllById(ids));
    }
}
