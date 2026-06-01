package com.sgd_hc.users.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.sgd_hc.users.dto.RoleCreateDto;
import com.sgd_hc.users.dto.RoleResponseDto;
import com.sgd_hc.users.dto.RoleUpdateDto;
import com.sgd_hc.users.service.RoleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/module_users/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    public ResponseEntity<RoleResponseDto> createRole(@RequestBody RoleCreateDto dto) {
        return new ResponseEntity<>(roleService.createRole(dto), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ResponseEntity<List<RoleResponseDto>> getAllRoles() {
        return ResponseEntity.ok(roleService.getAllRoles());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ResponseEntity<RoleResponseDto> getRoleById(@PathVariable UUID id) {
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    public ResponseEntity<RoleResponseDto> updateRole(@PathVariable UUID id, @RequestBody RoleUpdateDto dto) {
        return ResponseEntity.ok(roleService.updateRole(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
