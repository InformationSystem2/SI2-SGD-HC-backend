package com.sgd_hc.users.mapper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sgd_hc.users.dto.UserCreateDto;
import com.sgd_hc.users.dto.UserResponseDto;
import com.sgd_hc.users.dto.UserUpdateDto;
import com.sgd_hc.users.entity.DocumentType;
import com.sgd_hc.users.entity.Role;
import com.sgd_hc.users.entity.User;

@Component
public class UserMapper {

    private static final Set<String> ALL_READ_AUTHORITIES = Set.of(
            "user:read:id",
            "user:read:username",
            "user:read:email",
            "user:read:first_name",
            "user:read:last_name",
            "user:read:phone",
            "user:read:document_type",
            "user:read:document_number",
            "user:read:gender",
            "user:read:is_active",
            "user:read:roles"
    );

    public User toEntity(UserCreateDto dto, Set<Role> roles) {
        return toEntity(dto, roles, Set.of());
    }

    public User toEntity(UserCreateDto dto, Set<Role> roles, Set<String> userAuthorities) {
        return User.builder()
                .documentType(dto.documentType() != null ? DocumentType.valueOf(dto.documentType()) : DocumentType.CI)
                .documentNumber(dto.documentNumber())
                .email(dto.email())
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .phone(dto.phone())
                .gender(dto.gender())
                .roles(roles != null ? roles : new HashSet<>())
                .build();
    }

    public void updateEntityFromDto(UserUpdateDto dto, User existingUser, Set<Role> roles) {
        if (dto.documentType() != null) existingUser.setDocumentType(DocumentType.valueOf(dto.documentType()));
        if (dto.documentNumber() != null) existingUser.setDocumentNumber(dto.documentNumber());
        if (dto.email() != null) existingUser.setEmail(dto.email());
        if (dto.firstName() != null) existingUser.setFirstName(dto.firstName());
        if (dto.lastName() != null) existingUser.setLastName(dto.lastName());
        if (dto.phone() != null) existingUser.setPhone(dto.phone());
        if (dto.gender() != null) existingUser.setGender(dto.gender());
        if (dto.isActive() != null) existingUser.setIsActive(dto.isActive());
        if (roles != null) existingUser.setRoles(roles);
    }

    public UserResponseDto toResponseDto(User entity) {
        return toResponseDto(entity, ALL_READ_AUTHORITIES);
    }

    public UserResponseDto toResponseDto(User entity, Set<String> userAuthorities) {
        Set<Long> roleIds = entity.getRoles() != null
                ? entity.getRoles().stream()
                        .map(Role::getId)
                        .collect(Collectors.toSet())
                : new HashSet<>();

        return new UserResponseDto(
                canRead(userAuthorities, "id") ? entity.getId() : null,
                canRead(userAuthorities, "username") ? entity.getUsername() : null,
                canRead(userAuthorities, "email") ? entity.getEmail() : null,
                canRead(userAuthorities, "first_name") ? entity.getFirstName() : null,
                canRead(userAuthorities, "last_name") ? entity.getLastName() : null,
                canRead(userAuthorities, "phone") ? entity.getPhone() : null,
                canRead(userAuthorities, "document_type") && entity.getDocumentType() != null ? entity.getDocumentType().name() : null,
                canRead(userAuthorities, "document_number") ? entity.getDocumentNumber() : null,
                canRead(userAuthorities, "gender") ? entity.getGender() : null,
                canRead(userAuthorities, "is_active") ? entity.getIsActive() : null,
                canRead(userAuthorities, "roles") ? roleIds : null
        );
    }

    private boolean canRead(Set<String> userAuthorities, String attribute) {
        return userAuthorities.contains("user:read:" + attribute);
    }

    public Map<String, Object> toAuditMap(User entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId().toString());
        map.put("username", entity.getUsername());
        map.put("email", entity.getEmail());
        map.put("firstName", entity.getFirstName());
        map.put("lastName", entity.getLastName());
        map.put("documentType", entity.getDocumentType() != null ? entity.getDocumentType().name() : null);
        map.put("documentNumber", entity.getDocumentNumber());
        map.put("phone", entity.getPhone());
        map.put("gender", entity.getGender());
        map.put("isActive", entity.getIsActive());
        map.put("password", entity.getPassword());
        map.put("roles", entity.getRoles() != null ? entity.getRoles().stream().map(Role::getName).collect(Collectors.toSet()) : null);
        map.put("tenantId", entity.getTenant() != null ? entity.getTenant().getId().toString() : null);
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }

    public Map<String, Object> toAuditMapFromDto(UserResponseDto dto) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", dto.id().toString());
        map.put("username", dto.username());
        map.put("email", dto.email());
        map.put("firstName", dto.firstName());
        map.put("lastName", dto.lastName());
        map.put("documentType", dto.documentType());
        map.put("documentNumber", dto.documentNumber());
        map.put("phone", dto.phone());
        map.put("gender", dto.gender());
        map.put("isActive", dto.isActive());
        map.put("rolesIds", dto.rolesIds());
        return map;
    }    
}
