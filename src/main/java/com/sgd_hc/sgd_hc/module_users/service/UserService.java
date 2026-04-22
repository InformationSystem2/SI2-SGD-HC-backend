package com.sgd_hc.sgd_hc.module_users.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sgd_hc.sgd_hc.module_users.dto.UserCreateDto;
import com.sgd_hc.sgd_hc.module_users.dto.UserResponseDto;
import com.sgd_hc.sgd_hc.module_users.dto.UserUpdateDto;
import com.sgd_hc.sgd_hc.module_users.entity.Role;
import com.sgd_hc.sgd_hc.module_users.entity.User;
import com.sgd_hc.sgd_hc.module_users.mapper.UserMapper;
import com.sgd_hc.sgd_hc.module_users.repository.RoleRepository;
import com.sgd_hc.sgd_hc.module_users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Servicio encargado de gestionar la lógica de negocio para la entidad User.
 * Utiliza DTOs (records) para la entrada y salida de datos, desacoplando la capa de presentación de la base de datos.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository; 
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * Crea un nuevo usuario en el sistema a partir de un DTO.
     *
     * @param dto El record con los datos de creación.
     * @return UserResponseDto DTO con la información del usuario recién creado.
     * @throws IllegalArgumentException si el CI o el email ya están registrados.
     */
   @Transactional
    public UserResponseDto createUser(UserCreateDto dto) {

        /*if (userRepository.existsByCi(dto.ci()))
            throw new IllegalArgumentException("CI already exists");*/

        if (userRepository.existsByEmail(dto.email()))
            throw new IllegalArgumentException("Email already exists");

        // Obtener los roles a partir de los IDs proporcionados en el DTO
        Set<Role> roles = new HashSet<>();
        if (dto.rolesIds() != null && !dto.rolesIds().isEmpty()) {
            roles.addAll(roleRepository.findAllById(dto.rolesIds()));
        }

        // Convertir el DTO a entidad, asignando los roles obtenidos
        User user = userMapper.toEntity(dto, roles);
        user.setUsername(generateUsername());
        user.setPassword(passwordEncoder.encode(dto.password()));
        
        // Guardar el nuevo usuario en la base de datos y convertirlo a DTO de respuesta
        User savedUser = userRepository.save(user);
        return userMapper.toResponseDto(savedUser);
    }

    /**
     * Busca un usuario por su identificador único (ID).
     */
    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id) {
        return userMapper.toResponseDto(
                userRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id)));
    }

    /**
     * Busca un usuario por su correo electrónico.
     */
    @Transactional(readOnly = true)
    public UserResponseDto getUserByEmail(String email) {
        return userMapper.toResponseDto(
                userRepository.findByEmail(email)
                        .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email)));
    }

    /**
     * Recupera todos los usuarios registrados en el sistema.
     */
    @Transactional(readOnly = true)
    public Iterable<UserResponseDto> getAllUsers() {
        return userRepository.findAllRegularUsers().stream()
                .map(userMapper::toResponseDto)
                .toList();
    }

    /**.map(userMapper::toResponseDt
     * Actualiza la información de un usuario existente a partir de un DTO de actualización.
     *
     * @param id El ID del usuario que se desea actualizar.
     * @param dto Objeto record con los nuevos datos del usuario.
     * @return UserResponseDto DTO con la información del usuario actualizado.
     * @throws IllegalArgumentException si el usuario no existe o si el CI/Email entran en conflicto.
     */
    @Transactional
    public UserResponseDto updateUser(Long id, UserUpdateDto dto) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));


        // Obtener los roles a partir de los IDs proporcionados en el DTO
        Set<Role> roles = null;
        if (dto.rolesIds() != null) {
            roles = new HashSet<>(roleRepository.findAllById(dto.rolesIds()));
        }

        // Actualizar la entidad existente con los nuevos datos del DTO, incluyendo los roles
        userMapper.updateEntityFromDto(dto, existingUser, roles);

        if (dto.password() != null && !dto.password().isBlank())
            existingUser.setPassword(passwordEncoder.encode(dto.password()));

        // Guardar los cambios en la base de datos y convertir el usuario actualizado a DTO de respuesta
        User savedUser = userRepository.save(existingUser);
        return userMapper.toResponseDto(savedUser);
    }

    /**
     * Realiza un borrado lógico (soft delete) del usuario.
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        user.setIsActive(false);
        userRepository.save(user);
    }


    private String generateUsername() {
        String prefijo = "USR";

        String generatedCode;
        do {
            int randomNumber = (int) (Math.random() * 9000) + 1000;

            generatedCode = prefijo + "-" + randomNumber;

        } while (userRepository.existsByUsername(generatedCode));

        return generatedCode;
    }
}