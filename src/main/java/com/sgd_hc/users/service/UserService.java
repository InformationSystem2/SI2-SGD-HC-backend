package com.sgd_hc.users.service;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.service.AuditableService;

import com.sgd_hc.security.config.tenant.TenantContext;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import static com.sgd_hc.security.utils.SecurityUtils.*;
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
public class UserService implements AuditableService<UUID, User> {

    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final UserMapper            userMapper;
    private final PasswordEncoder       passwordEncoder;
    private final TenantResolverService tenantResolverService;

    @Transactional
    @Auditable(resourceType = "USER", actionType = ActionType.CREATE)
    public UserResponseDto createUser(UserCreateDto dto) {
        Set<String> authorities = currentAuthorities();
        validateCreateAttributePermissions(dto, authorities);

        if (userRepository.existsByEmail(dto.email()))
            throw new IllegalArgumentException("Email already exists");

        Set<Role> roles = new HashSet<>();
        if (dto.rolesIds() != null && !dto.rolesIds().isEmpty())
            roles.addAll(roleRepository.findAllById(dto.rolesIds()));

        User user = userMapper.toEntity(dto, roles);
        user.setUsername(generateUsername());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setTenant(tenantResolverService.resolve());

        return userMapper.toResponseDto(userRepository.save(user), authorities);
    }

    @Transactional(readOnly = true)
    @Auditable(resourceType = "USER", actionType = ActionType.READ, idParamName = "id")
    public UserResponseDto getUserById(UUID id) {
        return userMapper.toResponseDto(findOrThrow(id), currentAuthorities());
    }

    @Transactional(readOnly = true)
    @Auditable(resourceType = "USER", actionType = ActionType.READ)
    public UserResponseDto getUserByEmail(String email) {
        return userMapper.toResponseDto(
                userRepository.findByEmail(email)
                        .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email)),
                currentAuthorities());
    }

    @Transactional(readOnly = true)
    @Auditable(resourceType = "USER", actionType = ActionType.READ)
    public Iterable<UserResponseDto> getAllUsers() {
        Set<String> authorities = currentAuthorities();
        return userRepository.findAllRegularUsers().stream()
                .map(user -> userMapper.toResponseDto(user, authorities))
                .toList();
    }

    @Transactional
    @Auditable(resourceType = "USER", actionType = ActionType.UPDATE, idParamName = "id")
    public UserResponseDto updateUser(UUID id, UserUpdateDto dto) {
        Set<String> authorities = currentAuthorities();
        validateUpdateAttributePermissions(dto, authorities);

        User existingUser = findOrThrow(id);

        Set<Role> roles = null;
        if (dto.rolesIds() != null)
            roles = new HashSet<>(roleRepository.findAllById(dto.rolesIds()));

        userMapper.updateEntityFromDto(dto, existingUser, roles);

        if (dto.password() != null && !dto.password().isBlank())
            existingUser.setPassword(passwordEncoder.encode(dto.password()));

        return userMapper.toResponseDto(userRepository.save(existingUser), authorities);
    }

    @Transactional
    @Auditable(resourceType = "USER", actionType = ActionType.DELETE, idParamName = "id")
    public void deleteUser(UUID id) {
        userRepository.deleteById(id);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
    }

    /**
     * Válida que el usuario tenga permisos para crear los atributos especificados en el DTO
     * @param dto
     * @param authorities
     */
    private void validateCreateAttributePermissions(UserCreateDto dto, Set<String> authorities) {
        if (dto.phone() != null) requireAuthority(authorities, "user:create:phone");
        if (dto.gender() != null) requireAuthority(authorities, "user:create:gender");
        if (dto.rolesIds() != null) requireAuthority(authorities, "user:update:roles");
    }

    private void validateUpdateAttributePermissions(UserUpdateDto dto, Set<String> authorities) {
        if (dto.documentType() != null) requireAuthority(authorities, "user:update:document_type");
        if (dto.documentNumber() != null) requireAuthority(authorities, "user:update:document_number");
        if (dto.email() != null) requireAuthority(authorities, "user:update:email");
        if (dto.firstName() != null) requireAuthority(authorities, "user:update:first_name");
        if (dto.lastName() != null) requireAuthority(authorities, "user:update:last_name");
        if (dto.password() != null) requireAuthority(authorities, "user:update:password");
        if (dto.phone() != null) requireAuthority(authorities, "user:update:phone");
        if (dto.gender() != null) requireAuthority(authorities, "user:update:gender");
        if (dto.isActive() != null) requireAuthority(authorities, "user:update:is_active");
        if (dto.rolesIds() != null) requireAuthority(authorities, "user:update:roles");
    }


    public String generateUsername() {
        String slug = TenantContext.getCurrentTenantSlug();
        String prefix = "usr";
        String username;
        
        // Activar bypass para verificar unicidad global
        TenantContext.setBypassFilter(true);
        try {
            do {
                int n = (int) (Math.random() * 9000) + 1000;
                username = prefix + "-" + n + (slug != null ? "." + slug : "");
            } while (userRepository.existsByUsername(username));
        } finally {
            TenantContext.setBypassFilter(false);
        }
        return username;
    }

    @Override
    public User getEntity(UUID id) {
        return findOrThrow(id);
    }

    @Override
    public Map<String, Object> toAuditMap(User entity) {
        return userMapper.toAuditMap(entity);
    }
}
