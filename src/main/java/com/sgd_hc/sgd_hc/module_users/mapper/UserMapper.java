package com.sgd_hc.sgd_hc.module_users.mapper;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sgd_hc.sgd_hc.module_users.dto.UserCreateDto;
import com.sgd_hc.sgd_hc.module_users.dto.UserResponseDto;
import com.sgd_hc.sgd_hc.module_users.dto.UserUpdateDto;
import com.sgd_hc.sgd_hc.module_users.entity.Role;
import com.sgd_hc.sgd_hc.module_users.entity.User;

@Component
public class UserMapper {

    /**
     * Convierte un UserCreateDto a una entidad User, asignando los roles correspondientes.
     * @param dto El DTO que contiene los datos para crear el usuario.
     * @param roles Los roles a asignar al usuario.
     * @return La entidad User creada.
     */
    public User toEntity(UserCreateDto dto, Set<Role> roles) {
        return User.builder()
                .documentType(dto.documentType())
                .documentNumber(dto.documentNumber())
                .email(dto.email())
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .phone(dto.phone())
                .gender(dto.gender())

                .roles(roles != null ? roles : new HashSet<>()) // Asigna los roles que le pasaron
                .build();
    }

    /**
     * Actualiza una entidad User existente con los datos de un UserUpdateDto, incluyendo la actualización de roles.
     * @param dto El DTO que contiene los nuevos datos para actualizar el usuario.
     * @param existingUser La entidad User existente que se va a actualizar.
     * @param roles Los nuevos roles a asignar al usuario (puede ser null para mantener los roles actuales).
     */
    public void updateEntityFromDto(UserUpdateDto dto, User existingUser, Set<Role> roles) {
        existingUser.setDocumentType(dto.documentType());
        existingUser.setDocumentNumber(dto.documentNumber());
        existingUser.setFirstName(dto.firstName());
        existingUser.setLastName(dto.lastName());
        existingUser.setIsActive(dto.isActive());

        if (roles != null)
            existingUser.setRoles(roles);
        else
            existingUser.getRoles().clear();

    }

    /**
     * Convierte una entidad User a un UserResponseDto.
     * @param entity La entidad User a convertir.
     * @return El DTO con la información del usuario.
     */
    public UserResponseDto toResponseDto(User entity) {
        Set<Long> roleIds = entity.getRoles() != null
                ? entity.getRoles().stream()
                  .map(Role::getId)
                  .collect(Collectors.toSet())
                : new HashSet<>();

        return new UserResponseDto(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),           // email
                entity.getFirstName(),       // firstName
                entity.getLastName(),        // lastName
                entity.getPhone(),           // phone
                entity.getDocumentType(),    // documentType
                entity.getDocumentNumber(),  // documentNumber
                entity.getGender(),        // Boolean isActive  ← ojo aquí
                entity.getIsActive(),          // String gender     ← ojo aquí
                roleIds
        );
    }
}