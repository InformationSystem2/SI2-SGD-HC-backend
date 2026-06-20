package com.sgd_hc.patients.service;

import com.sgd_hc.security.exception.PlanLimitExceededException;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.PlanLimitValidator;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.patients.dto.PatientCreateDto;
import com.sgd_hc.patients.dto.PatientResponseDto;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.patients.mapper.PatientMapper;
import com.sgd_hc.patients.repository.PatientRepository;
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
class PatientServicePlanLimitTest {

    @Mock private PatientRepository patientRepository;
    @Mock private PatientMapper patientMapper;
    @Mock private TenantResolverService tenantResolverService;
    @Mock private PlanLimitValidator planLimitValidator;

    @InjectMocks private PatientService patientService;

    private Tenant tenant;
    private PatientCreateDto createDto;
    private Patient patientEntity;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());

        createDto = new PatientCreateDto(
                null, null, "Jane", "Smith",
                null, null, null, null
        );

        patientEntity = new Patient();
        patientEntity.setId(UUID.randomUUID());
        patientEntity.setTenant(tenant);

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
                () -> "patient:create:document_type",
                () -> "patient:create:document_number",
                () -> "patient:create:gender",
                () -> "patient:create:phone",
                () -> "patient:create:address"
        );
        doReturn(auths).when(authentication).getAuthorities();
        when(authentication.isAuthenticated()).thenReturn(true);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Nested
    @DisplayName("createPatient plan limit enforcement")
    class CreatePatientPlanLimitTests {

        @Test
        @DisplayName("calls planLimitValidator.checkPatientsLimit before saving")
        void callsValidatorBeforeSave() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            when(patientMapper.toEntity(any())).thenReturn(patientEntity);
            when(patientMapper.toResponseDto(any(), any())).thenReturn(mock(PatientResponseDto.class));
            when(patientRepository.save(any())).thenReturn(patientEntity);

            patientService.createPatient(createDto);

            verify(planLimitValidator).checkPatientsLimit(tenant.getId());
        }

        @Test
        @DisplayName("propagates PlanLimitExceededException from validator")
        void propagatesPlanLimitException() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            doThrow(new PlanLimitExceededException("pacientes", 100L, 100L))
                    .when(planLimitValidator).checkPatientsLimit(tenant.getId());

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> patientService.createPatient(createDto));

            assertEquals("pacientes", ex.getResourceType());
            verify(patientRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not save patient when limit exceeded")
        void doesNotSaveWhenLimitExceeded() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            doThrow(new PlanLimitExceededException("pacientes", 100L, 100L))
                    .when(planLimitValidator).checkPatientsLimit(tenant.getId());

            assertThrows(PlanLimitExceededException.class,
                    () -> patientService.createPatient(createDto));

            verify(patientRepository, never()).save(any());
            verify(patientMapper, never()).toEntity(any());
        }

        @Test
        @DisplayName("proceeds normally when under limit")
        void proceedsWhenUnderLimit() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            when(patientMapper.toEntity(any())).thenReturn(patientEntity);
            when(patientMapper.toResponseDto(any(), any())).thenReturn(mock(PatientResponseDto.class));
            when(patientRepository.save(any())).thenReturn(patientEntity);

            PatientResponseDto result = patientService.createPatient(createDto);

            assertNotNull(result);
            verify(patientRepository).save(any());
        }
    }
}
