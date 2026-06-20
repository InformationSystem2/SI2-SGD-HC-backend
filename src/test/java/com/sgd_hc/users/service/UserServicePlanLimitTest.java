package com.sgd_hc.users.service;

import com.sgd_hc.security.exception.PlanLimitExceededException;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.PlanLimitValidator;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.dto.UserCreateDto;
import com.sgd_hc.users.dto.UserResponseDto;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.mapper.UserMapper;
import com.sgd_hc.users.repository.RoleRepository;
import com.sgd_hc.users.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServicePlanLimitTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantResolverService tenantResolverService;
    @Mock private PlanLimitValidator planLimitValidator;

    @InjectMocks private UserService userService;

    private Tenant tenant;
    private UserCreateDto createDto;
    private User userEntity;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());

        createDto = new UserCreateDto(
                null, null, "john@test.com", "John", "Doe",
                "password123", null, null, null
        );

        userEntity = new User();
        userEntity.setId(UUID.randomUUID());
        userEntity.setTenant(tenant);

        setupSecurityContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getAuthorities()).thenReturn(Collections.emptyList());
        when(authentication.isAuthenticated()).thenReturn(true);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Nested
    @DisplayName("createUser plan limit enforcement")
    class CreateUserPlanLimitTests {

        @Test
        @DisplayName("calls planLimitValidator.checkUsersLimit before saving")
        void callsValidatorBeforeSave() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(userMapper.toEntity(any(), any())).thenReturn(userEntity);
            when(userMapper.toResponseDto(any(), any())).thenReturn(mock(UserResponseDto.class));
            when(passwordEncoder.encode(any())).thenReturn("encoded");

            userService.createUser(createDto);

            verify(planLimitValidator).checkUsersLimit(tenant.getId());
        }

        @Test
        @DisplayName("propagates PlanLimitExceededException from validator")
        void propagatesPlanLimitException() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            doThrow(new PlanLimitExceededException("usuarios", 10L, 10L))
                    .when(planLimitValidator).checkUsersLimit(tenant.getId());

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> userService.createUser(createDto));

            assertEquals("usuarios", ex.getResourceType());
            assertEquals(10L, ex.getCurrentCount());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not save user when limit exceeded")
        void doesNotSaveWhenLimitExceeded() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            doThrow(new PlanLimitExceededException("usuarios", 10L, 10L))
                    .when(planLimitValidator).checkUsersLimit(tenant.getId());

            assertThrows(PlanLimitExceededException.class,
                    () -> userService.createUser(createDto));

            verify(userRepository, never()).save(any());
            verify(userMapper, never()).toEntity(any(), any());
        }

        @Test
        @DisplayName("proceeds normally when under limit")
        void proceedsWhenUnderLimit() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(userMapper.toEntity(any(), any())).thenReturn(userEntity);
            when(userMapper.toResponseDto(any(), any())).thenReturn(mock(UserResponseDto.class));
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(userRepository.save(any())).thenReturn(userEntity);

            UserResponseDto result = userService.createUser(createDto);

            assertNotNull(result);
            verify(userRepository).save(any());
        }
    }
}
