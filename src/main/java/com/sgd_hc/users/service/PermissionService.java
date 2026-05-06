package com.sgd_hc.users.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sgd_hc.users.dto.PermissionCreateDto;
import com.sgd_hc.users.dto.PermissionResponseDto;
import com.sgd_hc.users.dto.PermissionUpdateDto;
import com.sgd_hc.users.entity.Permission;
import com.sgd_hc.users.mapper.PermissionMapper;
import com.sgd_hc.users.repository.PermissionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    @Transactional
    public PermissionResponseDto createPermission(PermissionCreateDto dto) {
        validateNameUniqueness(dto.name(), null);
        Permission permission = permissionMapper.toEntity(dto);
        return permissionMapper.toResponseDto(permissionRepository.save(permission));
    }

    @Transactional(readOnly = true)
    public PermissionResponseDto getPermissionById(UUID id) {
        return permissionMapper.toResponseDto(findPermissionOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponseDto> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(permissionMapper::toResponseDto)
                .toList();
    }

    @Transactional
    public PermissionResponseDto updatePermission(UUID id, PermissionUpdateDto dto) {
        Permission existingPermission = findPermissionOrThrow(id);

        if (dto.name() != null)
            validateNameUniqueness(dto.name(), id);

        permissionMapper.updateEntityFromDto(dto, existingPermission);
        return permissionMapper.toResponseDto(permissionRepository.save(existingPermission));
    }

    @Transactional
    public void deletePermission(UUID id) {
        Permission permission = findPermissionOrThrow(id);
        permission.setIsActive(false);
        permissionRepository.save(permission);
    }

    private Permission findPermissionOrThrow(UUID id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found with id: " + id));
    }

    private void validateNameUniqueness(String name, UUID id) {
        permissionRepository.findByName(name).ifPresent(p -> {
            if (id == null || !p.getId().equals(id))
                throw new IllegalArgumentException("Permission name already exists: " + name);
        });
    }
}
