package com.sgd_hc.sgd_hc.module_users.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sgd_hc.sgd_hc.module_users.dto.PermissionCreateDto;
import com.sgd_hc.sgd_hc.module_users.dto.PermissionResponseDto;
import com.sgd_hc.sgd_hc.module_users.dto.PermissionUpdateDto;
import com.sgd_hc.sgd_hc.module_users.entity.Permission;
import com.sgd_hc.sgd_hc.module_users.mapper.PermissionMapper;
import com.sgd_hc.sgd_hc.module_users.repository.PermissionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Servicio encargado de gestionar la lógica de negocio para la entidad Permission.
 * Adoptando el patrón de "Helpers" para un código más limpio y mantenible.
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    /**
     * Crea un nuevo permiso.
     */
    @Transactional
    public PermissionResponseDto createPermission(PermissionCreateDto dto) {
        validateNameUniqueness(dto.name(), null);
        
        Permission permission = permissionMapper.toEntity(dto);
        return permissionMapper.toResponseDto(permissionRepository.save(permission));
    }

    /**
     * Busca un permiso por su ID.
     */
    @Transactional(readOnly = true)
    public PermissionResponseDto getPermissionById(Long id) {
        return permissionMapper.toResponseDto(findPermissionOrThrow(id));
    }

    /**
     * Recupera todos los permisos.
     */
    @Transactional(readOnly = true)
    public List<PermissionResponseDto> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(permissionMapper::toResponseDto)
                .toList();
    }

    /**
     * Actualiza un permiso existente.
     */
    @Transactional
    public PermissionResponseDto updatePermission(Long id, PermissionUpdateDto dto) {
        Permission existingPermission = findPermissionOrThrow(id);
        
        if (dto.name() != null) {
            validateNameUniqueness(dto.name(), id);
        }

        permissionMapper.updateEntityFromDto(dto, existingPermission);
        return permissionMapper.toResponseDto(permissionRepository.save(existingPermission));
    }

    /**
     * Borrado lógico de un permiso.
     */
    @Transactional
    public void deletePermission(Long id) {
        Permission permission = findPermissionOrThrow(id);
        permission.setActive(false);
        permissionRepository.save(permission);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Busca un permiso por ID o lanza una excepción si no existe.
     */
    private Permission findPermissionOrThrow(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found with id: " + id));
    }

    /**
     * Valida que el nombre sea único, ignorando el ID actual en caso de actualización.
     */
    private void validateNameUniqueness(String name, Long id) {
        permissionRepository.findByName(name).ifPresent(p -> {
            if (id == null || !p.getId().equals(id)) {
                throw new IllegalArgumentException("Permission name already exists: " + name);
            }
        });
    }
}
