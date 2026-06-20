package com.sgd_hc.documents.service;

import com.sgd_hc.documents.dto.DocumentRequestDto;
import com.sgd_hc.documents.dto.DocumentResponseDto;
import com.sgd_hc.documents.dto.ExternalDocumentRequestDto;
import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.entity.DocumentStatus;
import com.sgd_hc.documents.entity.DocumentTemplate;
import com.sgd_hc.documents.mapper.DocumentMapper;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.documents.repository.DocumentOcrMetadataRepository;
import com.sgd_hc.documents.repository.DocumentTemplateRepository;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.patients.repository.PatientRepository;
import com.sgd_hc.security.exception.PlanLimitExceededException;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.PlanLimitValidator;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.users.entity.User;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentServicePlanLimitTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentTemplateRepository documentTemplateRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private DocumentMapper documentMapper;
    @Mock private TenantResolverService tenantResolverService;
    @Mock private OcrClientService ocrClientService;
    @Mock private DocumentOcrMetadataRepository ocrMetadataRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private PlanLimitValidator planLimitValidator;

    @InjectMocks private DocumentService documentService;

    private Tenant tenant;
    private Patient patient;
    private Document document;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());

        patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setTenant(tenant);

        document = new Document();
        document.setId(UUID.randomUUID());
        document.setTenant(tenant);
        document.setPatient(patient);
        document.setStatus(DocumentStatus.DRAFT);

        User uploader = new User();
        uploader.setId(UUID.randomUUID());
        uploader.setTenant(tenant);
        uploader.setIsActive(true);

        setupSecurityContext(uploader);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext(User uploader) {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        List<GrantedAuthority> auths = List.<GrantedAuthority>of(
                () -> "document:create:template_id",
                () -> "document:create:clinical_content",
                () -> "document:create:file_url",
                () -> "document:create:is_external_source"
        );
        doReturn(auths).when(authentication).getAuthorities();
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(new SecurityUser(uploader));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Nested
    @DisplayName("create plan limit enforcement")
    class CreatePlanLimitTests {

        private DocumentRequestDto createDto;

        @BeforeEach
        void setUp() {
            createDto = new DocumentRequestDto(
                    patient.getId(), UUID.randomUUID(), Map.of(),
                    LocalDate.now(), null, null, false
            );
        }

        @Test
        @DisplayName("calls planLimitValidator.checkDocumentsLimit before saving")
        void callsValidatorBeforeSave() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            when(patientRepository.findById(any())).thenReturn(Optional.of(patient));
            when(documentTemplateRepository.findByIdAndTenantId(any(), any()))
                    .thenReturn(Optional.of(new DocumentTemplate()));
            when(documentMapper.toEntity(any(), any(), any(), any())).thenReturn(document);
            when(documentMapper.toResponseDto(any(), any())).thenReturn(mock(DocumentResponseDto.class));
            when(documentRepository.save(any())).thenReturn(document);

            documentService.create(createDto);

            verify(planLimitValidator).checkDocumentsLimit(tenant.getId());
        }

        @Test
        @DisplayName("propagates PlanLimitExceededException from validator")
        void propagatesPlanLimitException() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            doThrow(new PlanLimitExceededException("documentos", 500L, 500L))
                    .when(planLimitValidator).checkDocumentsLimit(tenant.getId());

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> documentService.create(createDto));

            assertEquals("documentos", ex.getResourceType());
            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not save document when limit exceeded")
        void doesNotSaveWhenLimitExceeded() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            doThrow(new PlanLimitExceededException("documentos", 500L, 500L))
                    .when(planLimitValidator).checkDocumentsLimit(tenant.getId());

            assertThrows(PlanLimitExceededException.class,
                    () -> documentService.create(createDto));

            verify(documentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("createExternal plan limit enforcement")
    class CreateExternalPlanLimitTests {

        private ExternalDocumentRequestDto externalDto;

        @BeforeEach
        void setUp() {
            externalDto = new ExternalDocumentRequestDto(
                    patient.getId(), "/uploads/test.pdf", LocalDate.now(), "notes"
            );
        }

        @Test
        @DisplayName("calls planLimitValidator.checkDocumentsLimit before saving")
        void callsValidatorBeforeSave() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            when(patientRepository.findById(any())).thenReturn(Optional.of(patient));
            when(documentMapper.toResponseDto(any(), any())).thenReturn(mock(DocumentResponseDto.class));
            when(documentRepository.save(any())).thenReturn(document);

            documentService.createExternal(externalDto);

            verify(planLimitValidator).checkDocumentsLimit(tenant.getId());
        }

        @Test
        @DisplayName("propagates PlanLimitExceededException from validator")
        void propagatesPlanLimitException() {
            when(tenantResolverService.resolve()).thenReturn(tenant);
            doThrow(new PlanLimitExceededException("documentos", 500L, 500L))
                    .when(planLimitValidator).checkDocumentsLimit(tenant.getId());

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> documentService.createExternal(externalDto));

            assertEquals("documentos", ex.getResourceType());
            verify(documentRepository, never()).save(any());
        }
    }
}
