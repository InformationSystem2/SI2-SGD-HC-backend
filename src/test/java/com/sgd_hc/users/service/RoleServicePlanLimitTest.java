package com.sgd_hc.users.service;

import com.sgd_hc.security.exception.PlanLimitExceededException;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.PlanLimitValidator;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.dto.RoleCreateDto;
import com.sgd_hc.users.dto.RoleResponseDto;
import com.sgd_hc.users.entity.Role;
import com.sgd_hc.users.mapper.RoleMapper;
import com.sgd_hc.users.repository.PermissionRepository;
import com.sgd_hc.users.repository.RoleRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleServicePlanLimitTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RoleMapper roleMapper;
    @Mock private TenantResolverService tenantResolverService;
    @Mock private PlanLimitValidator planLimitValidator;

    @InjectMocks private RoleService roleService;

    private Tenant tenant;
    private RoleCreateDto createDto;
    private Role roleEntity;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());

        createDto = new RoleCreateDto("Doctor", "Medical staff", null);
        roleEntity = new Role();
        roleEntity.setId(1L);
        roleEntity.setTenant(tenant);

        setupSecurityContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        List<GrantedAuthority> auths = List.<GrantedAuthority>of(
                () -> "role:create:description",
                () -> "role:create:permissions"
        );
        doReturn(auths).when(authentication).getAuthorities();
        when(authentication.isAuthenticated()).thenReturn(true);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Nested
    @DisplayName("createRole plan limit enforcement")
    class CreateRolePlanLimitTests {

        @Test
        @DisplayName("calls planLimitValidator.checkStaffRolesLimit before saving")
        void callsValidatorBeforeSave() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            when(roleRepository.findByName(any())).thenReturn(Optional.empty());
            when(roleMapper.toEntity(any(), any())).thenReturn(roleEntity);
            when(roleMapper.toResponseDto(any(), any())).thenReturn(mock(RoleResponseDto.class));
            when(roleRepository.save(any())).thenReturn(roleEntity);

            roleService.createRole(createDto);

            verify(planLimitValidator).checkStaffRolesLimit(tenant.getId());
        }

        @Test
        @DisplayName("propagates PlanLimitExceededException from validator")
        void propagatesPlanLimitException() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            doThrow(new PlanLimitExceededException("roles de staff", 5L, 5L))
                    .when(planLimitValidator).checkStaffRolesLimit(tenant.getId());

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> roleService.createRole(createDto));

            assertEquals("roles de staff", ex.getResourceType());
            verify(roleRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not save role when limit exceeded")
        void doesNotSaveWhenLimitExceeded() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            doThrow(new PlanLimitExceededException("roles de staff", 5L, 5L))
                    .when(planLimitValidator).checkStaffRolesLimit(tenant.getId());

            assertThrows(PlanLimitExceededException.class,
                    () -> roleService.createRole(createDto));

            verify(roleRepository, never()).save(any());
            verify(roleMapper, never()).toEntity(any(), any());
        }

        @Test
        @DisplayName("proceeds normally when under limit")
        void proceedsWhenUnderLimit() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            when(roleRepository.findByName(any())).thenReturn(Optional.empty());
            when(roleMapper.toEntity(any(), any())).thenReturn(roleEntity);
            when(roleMapper.toResponseDto(any(), any())).thenReturn(mock(RoleResponseDto.class));
            when(roleRepository.save(any())).thenReturn(roleEntity);

            RoleResponseDto result = roleService.createRole(createDto);

            assertNotNull(result);
            verify(roleRepository).save(any());
        }
    }
}
