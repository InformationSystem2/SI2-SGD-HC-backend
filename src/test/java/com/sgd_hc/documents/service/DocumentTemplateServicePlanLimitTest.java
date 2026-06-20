package com.sgd_hc.documents.service;

import com.sgd_hc.documents.dto.DocumentTemplateRequestDto;
import com.sgd_hc.documents.dto.DocumentTemplateResponseDto;
import com.sgd_hc.documents.entity.DocumentTemplate;
import com.sgd_hc.documents.mapper.DocumentTemplateMapper;
import com.sgd_hc.documents.repository.DocumentTemplateRepository;
import com.sgd_hc.security.exception.PlanLimitExceededException;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.PlanLimitValidator;
import com.sgd_hc.tenants.service.TenantResolverService;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentTemplateServicePlanLimitTest {

    @Mock private DocumentTemplateRepository documentTemplateRepository;
    @Mock private DocumentTemplateMapper documentTemplateMapper;
    @Mock private TenantResolverService tenantResolverService;
    @Mock private PlanLimitValidator planLimitValidator;

    @InjectMocks private DocumentTemplateService templateService;

    private Tenant tenant;
    private DocumentTemplateRequestDto createDto;
    private DocumentTemplate templateEntity;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());

        createDto = new DocumentTemplateRequestDto("Historia Clínica", "Template base", Map.of());
        templateEntity = new DocumentTemplate();
        templateEntity.setId(UUID.randomUUID());
        templateEntity.setTenant(tenant);

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
                () -> "template:create:name",
                () -> "template:create:description"
        );
        doReturn(auths).when(authentication).getAuthorities();
        when(authentication.isAuthenticated()).thenReturn(true);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Nested
    @DisplayName("create plan limit enforcement")
    class CreatePlanLimitTests {

        @Test
        @DisplayName("calls planLimitValidator.checkDocumentTemplatesLimit before saving")
        void callsValidatorBeforeSave() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            when(documentTemplateMapper.toEntity(any())).thenReturn(templateEntity);
            when(documentTemplateMapper.toResponseDto(any(), any())).thenReturn(mock(DocumentTemplateResponseDto.class));
            when(documentTemplateRepository.save(any())).thenReturn(templateEntity);

            templateService.create(createDto);

            verify(planLimitValidator).checkDocumentTemplatesLimit(tenant.getId());
        }

        @Test
        @DisplayName("propagates PlanLimitExceededException from validator")
        void propagatesPlanLimitException() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            doThrow(new PlanLimitExceededException("plantillas", 5L, 5L))
                    .when(planLimitValidator).checkDocumentTemplatesLimit(tenant.getId());

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> templateService.create(createDto));

            assertEquals("plantillas", ex.getResourceType());
            verify(documentTemplateRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not save template when limit exceeded")
        void doesNotSaveWhenLimitExceeded() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            doThrow(new PlanLimitExceededException("plantillas", 5L, 5L))
                    .when(planLimitValidator).checkDocumentTemplatesLimit(tenant.getId());

            assertThrows(PlanLimitExceededException.class,
                    () -> templateService.create(createDto));

            verify(documentTemplateRepository, never()).save(any());
            verify(documentTemplateMapper, never()).toEntity(any());
        }

        @Test
        @DisplayName("proceeds normally when under limit")
        void proceedsWhenUnderLimit() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            when(documentTemplateMapper.toEntity(any())).thenReturn(templateEntity);
            when(documentTemplateMapper.toResponseDto(any(), any())).thenReturn(mock(DocumentTemplateResponseDto.class));
            when(documentTemplateRepository.save(any())).thenReturn(templateEntity);

            DocumentTemplateResponseDto result = templateService.create(createDto);

            assertNotNull(result);
            verify(documentTemplateRepository).save(any());
        }
    }
}
