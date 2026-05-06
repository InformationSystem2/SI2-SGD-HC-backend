package com.sgd_hc.users.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.sgd_hc.users.dto.PermissionCreateDto;
import com.sgd_hc.users.dto.PermissionResponseDto;
import com.sgd_hc.users.dto.PermissionUpdateDto;
import com.sgd_hc.users.service.PermissionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/module_users/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_CREATE')")
    public ResponseEntity<PermissionResponseDto> createPermission(@RequestBody PermissionCreateDto dto) {
        return new ResponseEntity<>(permissionService.createPermission(dto), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    public ResponseEntity<List<PermissionResponseDto>> getAllPermissions() {
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    public ResponseEntity<PermissionResponseDto> getPermissionById(@PathVariable UUID id) {
        return ResponseEntity.ok(permissionService.getPermissionById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_UPDATE')")
    public ResponseEntity<PermissionResponseDto> updatePermission(@PathVariable UUID id, @RequestBody PermissionUpdateDto dto) {
        return ResponseEntity.ok(permissionService.updatePermission(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_DELETE')")
    public ResponseEntity<Void> deletePermission(@PathVariable UUID id) {
        permissionService.deletePermission(id);
        return ResponseEntity.noContent().build();
    }
}
