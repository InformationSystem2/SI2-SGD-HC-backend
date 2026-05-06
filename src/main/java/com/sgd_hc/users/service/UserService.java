package com.sgd_hc.users.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.dto.UserCreateDto;
import com.sgd_hc.users.dto.UserResponseDto;
import com.sgd_hc.users.dto.UserUpdateDto;
import com.sgd_hc.users.entity.Role;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.mapper.UserMapper;
import com.sgd_hc.users.repository.RoleRepository;
import com.sgd_hc.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final UserMapper            userMapper;
    private final PasswordEncoder       passwordEncoder;
    private final TenantResolverService tenantResolverService;

    @Transactional
    public UserResponseDto createUser(UserCreateDto dto) {
        if (userRepository.existsByEmail(dto.email()))
            throw new IllegalArgumentException("Email already exists");

        Set<Role> roles = new HashSet<>();
        if (dto.rolesIds() != null && !dto.rolesIds().isEmpty())
            roles.addAll(roleRepository.findAllById(dto.rolesIds()));

        User user = userMapper.toEntity(dto, roles);
        user.setUsername(generateUsername());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setTenant(tenantResolverService.resolve());

        return userMapper.toResponseDto(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(UUID id) {
        return userMapper.toResponseDto(
                userRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id)));
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserByEmail(String email) {
        return userMapper.toResponseDto(
                userRepository.findByEmail(email)
                        .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email)));
    }

    @Transactional(readOnly = true)
    public Iterable<UserResponseDto> getAllUsers() {
        return userRepository.findAllRegularUsers().stream()
                .map(userMapper::toResponseDto)
                .toList();
    }

    @Transactional
    public UserResponseDto updateUser(UUID id, UserUpdateDto dto) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

        Set<Role> roles = null;
        if (dto.rolesIds() != null)
            roles = new HashSet<>(roleRepository.findAllById(dto.rolesIds()));

        userMapper.updateEntityFromDto(dto, existingUser, roles);

        if (dto.password() != null && !dto.password().isBlank())
            existingUser.setPassword(passwordEncoder.encode(dto.password()));

        return userMapper.toResponseDto(userRepository.save(existingUser));
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        user.setIsActive(false);
        userRepository.save(user);
    }

    private String generateUsername() {
        String prefix = "USR";
        String code;
        do {
            int n = (int) (Math.random() * 9000) + 1000;
            code = prefix + "-" + n;
        } while (userRepository.existsByUsername(code));
        return code;
    }
}
