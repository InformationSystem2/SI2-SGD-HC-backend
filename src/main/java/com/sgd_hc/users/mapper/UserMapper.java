package com.sgd_hc.users.mapper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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

    public User toEntity(UserCreateDto dto, Set<Role> roles) {
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
        if (dto.firstName() != null) existingUser.setFirstName(dto.firstName());
        if (dto.lastName() != null) existingUser.setLastName(dto.lastName());
        if (dto.phone() != null) existingUser.setPhone(dto.phone());
        if (dto.isActive() != null) existingUser.setIsActive(dto.isActive());
        if (roles != null) existingUser.setRoles(roles);
    }

    public UserResponseDto toResponseDto(User entity) {
        Set<UUID> roleIds = entity.getRoles() != null
                ? entity.getRoles().stream()
                        .map(Role::getId)
                        .collect(Collectors.toSet())
                : new HashSet<>();

        return new UserResponseDto(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getPhone(),
                entity.getDocumentType() != null ? entity.getDocumentType().name() : null,
                entity.getDocumentNumber(),
                entity.getGender(),
                entity.getIsActive(),
                roleIds
        );
    }
}
