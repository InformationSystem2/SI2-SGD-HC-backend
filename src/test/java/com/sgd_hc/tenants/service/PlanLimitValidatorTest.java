package com.sgd_hc.tenants.service;

import com.sgd_hc.backups.repository.BackupHistoryRepository;
import com.sgd_hc.dicom.repository.DicomInstanceRepository;
import com.sgd_hc.dicom.repository.DicomStudyRepository;
import com.sgd_hc.documents.repository.DocumentOcrMetadataRepository;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.documents.repository.DocumentTemplateRepository;
import com.sgd_hc.documents.repository.ReportTemplateRepository;
import com.sgd_hc.patients.repository.PatientRepository;
import com.sgd_hc.security.exception.PlanLimitExceededException;
import com.sgd_hc.tenants.entity.SubscriptionPlan;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.ApiCallUsageRepository;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.users.repository.RoleRepository;
import com.sgd_hc.users.repository.UserRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanLimitValidatorTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private PlanService planService;
    @Mock private UserRepository userRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentTemplateRepository documentTemplateRepository;
    @Mock private DicomStudyRepository dicomStudyRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private DicomInstanceRepository dicomInstanceRepository;
    @Mock private DocumentOcrMetadataRepository ocrMetadataRepository;
    @Mock private ApiCallUsageRepository apiCallUsageRepository;
    @Mock private ReportTemplateRepository reportTemplateRepository;
    @Mock private BackupHistoryRepository backupHistoryRepository;

    @InjectMocks private PlanLimitValidator validator;

    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSubscriptionPlan(SubscriptionPlan.BASIC);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    }

    private void mockLimit(String key, long value) {
        when(planService.getLimitValue("BASIC", key)).thenReturn(value);
    }

    private void mockLimitForPlan(SubscriptionPlan plan, String key, long value) {
        when(planService.getLimitValue(plan.name(), key)).thenReturn(value);
    }

    // ── USERS ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkUsersLimit")
    class CheckUsersLimitTests {

        @Test
        @DisplayName("does not throw when under limit")
        void doesNotThrowWhenUnderLimit() {
            mockLimit("maxUsers", 10L);
            when(userRepository.countByTenantId(tenantId)).thenReturn(3L);
            assertDoesNotThrow(() -> validator.checkUsersLimit(tenantId));
        }

        @Test
        @DisplayName("does not throw when count is one below limit")
        void doesNotThrowWhenOneBelowLimit() {
            mockLimit("maxUsers", 10L);
            when(userRepository.countByTenantId(tenantId)).thenReturn(9L);
            assertDoesNotThrow(() -> validator.checkUsersLimit(tenantId));
        }

        @Test
        @DisplayName("throws when at limit (current == max)")
        void throwsWhenAtLimit() {
            mockLimit("maxUsers", 10L);
            when(userRepository.countByTenantId(tenantId)).thenReturn(10L);

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> validator.checkUsersLimit(tenantId));
            assertEquals("usuarios", ex.getResourceType());
            assertEquals(10L, ex.getCurrentCount());
            assertEquals(10L, ex.getMaxLimit());
        }

        @Test
        @DisplayName("throws when over limit")
        void throwsWhenOverLimit() {
            mockLimit("maxUsers", 10L);
            when(userRepository.countByTenantId(tenantId)).thenReturn(15L);

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> validator.checkUsersLimit(tenantId));
            assertEquals(15L, ex.getCurrentCount());
        }

        @Test
        @DisplayName("skips validation when limit is -1 (unlimited)")
        void skipsWhenUnlimited() {
            mockLimit("maxUsers", -1L);
            assertDoesNotThrow(() -> validator.checkUsersLimit(tenantId));
            verify(userRepository, never()).countByTenantId(any());
        }

        @Test
        @DisplayName("throws when limit is 0 and there are 0 users")
        void throwsWhenLimitZeroAndZeroUsers() {
            mockLimit("maxUsers", 0L);
            when(userRepository.countByTenantId(tenantId)).thenReturn(0L);
            assertThrows(PlanLimitExceededException.class,
                    () -> validator.checkUsersLimit(tenantId));
        }

        @Test
        @DisplayName("throws when limit is 0 and there is 1 user")
        void throwsWhenLimitZeroAndOneUser() {
            mockLimit("maxUsers", 0L);
            when(userRepository.countByTenantId(tenantId)).thenReturn(1L);
            assertThrows(PlanLimitExceededException.class,
                    () -> validator.checkUsersLimit(tenantId));
        }
    }

    // ── PATIENTS ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkPatientsLimit")
    class CheckPatientsLimitTests {

        @Test
        @DisplayName("does not throw when under limit")
        void doesNotThrowWhenUnderLimit() {
            mockLimit("maxPatients", 100L);
            when(patientRepository.countByTenantId(tenantId)).thenReturn(50L);
            assertDoesNotThrow(() -> validator.checkPatientsLimit(tenantId));
        }

        @Test
        @DisplayName("throws when at limit")
        void throwsWhenAtLimit() {
            mockLimit("maxPatients", 100L);
            when(patientRepository.countByTenantId(tenantId)).thenReturn(100L);

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> validator.checkPatientsLimit(tenantId));
            assertEquals("pacientes", ex.getResourceType());
        }

        @Test
        @DisplayName("skips when unlimited (-1)")
        void skipsWhenUnlimited() {
            mockLimit("maxPatients", -1L);
            assertDoesNotThrow(() -> validator.checkPatientsLimit(tenantId));
            verify(patientRepository, never()).countByTenantId(any());
        }
    }

    // ── DOCUMENTS ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkDocumentsLimit")
    class CheckDocumentsLimitTests {

        @Test
        @DisplayName("does not throw when under limit")
        void doesNotThrowWhenUnderLimit() {
            mockLimit("maxDocuments", 500L);
            when(documentRepository.countByTenantId(tenantId)).thenReturn(200L);
            assertDoesNotThrow(() -> validator.checkDocumentsLimit(tenantId));
        }

        @Test
        @DisplayName("throws when at limit")
        void throwsWhenAtLimit() {
            mockLimit("maxDocuments", 500L);
            when(documentRepository.countByTenantId(tenantId)).thenReturn(500L);

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> validator.checkDocumentsLimit(tenantId));
            assertEquals("documentos", ex.getResourceType());
            assertEquals(500L, ex.getCurrentCount());
            assertEquals(500L, ex.getMaxLimit());
        }

        @Test
        @DisplayName("skips when unlimited (-1)")
        void skipsWhenUnlimited() {
            mockLimit("maxDocuments", -1L);
            assertDoesNotThrow(() -> validator.checkDocumentsLimit(tenantId));
            verify(documentRepository, never()).countByTenantId(any());
        }
    }

    // ── DOCUMENT TEMPLATES ───────────────────────────────────────────────────

    @Nested
    @DisplayName("checkDocumentTemplatesLimit")
    class CheckDocumentTemplatesLimitTests {

        @Test
        @DisplayName("does not throw when under limit")
        void doesNotThrowWhenUnderLimit() {
            mockLimit("maxDocumentTemplates", 5L);
            when(documentTemplateRepository.countByTenantId(tenantId)).thenReturn(2L);
            assertDoesNotThrow(() -> validator.checkDocumentTemplatesLimit(tenantId));
        }

        @Test
        @DisplayName("throws when at limit")
        void throwsWhenAtLimit() {
            mockLimit("maxDocumentTemplates", 5L);
            when(documentTemplateRepository.countByTenantId(tenantId)).thenReturn(5L);

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> validator.checkDocumentTemplatesLimit(tenantId));
            assertEquals("plantillas", ex.getResourceType());
        }

        @Test
        @DisplayName("skips when unlimited (-1)")
        void skipsWhenUnlimited() {
            mockLimit("maxDocumentTemplates", -1L);
            assertDoesNotThrow(() -> validator.checkDocumentTemplatesLimit(tenantId));
            verify(documentTemplateRepository, never()).countByTenantId(any());
        }
    }

    // ── DICOM STUDIES ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkDicomStudiesLimit")
    class CheckDicomStudiesLimitTests {

        @Test
        @DisplayName("does not throw when under limit")
        void doesNotThrowWhenUnderLimit() {
            mockLimit("maxDicomStudies", 100L);
            when(dicomStudyRepository.countByTenantId(tenantId)).thenReturn(30L);
            assertDoesNotThrow(() -> validator.checkDicomStudiesLimit(tenantId));
        }

        @Test
        @DisplayName("throws when at limit")
        void throwsWhenAtLimit() {
            mockLimit("maxDicomStudies", 100L);
            when(dicomStudyRepository.countByTenantId(tenantId)).thenReturn(100L);

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> validator.checkDicomStudiesLimit(tenantId));
            assertEquals("estudios DICOM", ex.getResourceType());
        }

        @Test
        @DisplayName("skips when unlimited (-1)")
        void skipsWhenUnlimited() {
            mockLimit("maxDicomStudies", -1L);
            assertDoesNotThrow(() -> validator.checkDicomStudiesLimit(tenantId));
            verify(dicomStudyRepository, never()).countByTenantId(any());
        }
    }

    // ── STAFF ROLES ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkStaffRolesLimit")
    class CheckStaffRolesLimitTests {

        @Test
        @DisplayName("does not throw when under limit")
        void doesNotThrowWhenUnderLimit() {
            mockLimit("maxStaffRoles", 5L);
            when(roleRepository.countByTenantId(tenantId)).thenReturn(3L);
            assertDoesNotThrow(() -> validator.checkStaffRolesLimit(tenantId));
        }

        @Test
        @DisplayName("throws when at limit")
        void throwsWhenAtLimit() {
            mockLimit("maxStaffRoles", 5L);
            when(roleRepository.countByTenantId(tenantId)).thenReturn(5L);

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> validator.checkStaffRolesLimit(tenantId));
            assertEquals("roles de staff", ex.getResourceType());
        }

        @Test
        @DisplayName("skips when unlimited (-1)")
        void skipsWhenUnlimited() {
            mockLimit("maxStaffRoles", -1L);
            assertDoesNotThrow(() -> validator.checkStaffRolesLimit(tenantId));
            verify(roleRepository, never()).countByTenantId(any());
        }
    }

    // ── STORAGE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkStorageLimit")
    class CheckStorageLimitTests {

        @Test
        @DisplayName("does not throw when under limit")
        void doesNotThrowWhenUnderLimit() {
            mockLimit("maxStorageMB", 1024L);
            when(documentRepository.sumFileSizeBytesByTenantId(tenantId)).thenReturn(500L * 1024L * 1024L);
            when(dicomInstanceRepository.sumFileSizeBytesByTenantId(tenantId)).thenReturn(0L);

            long incomingBytes = 100L * 1024L * 1024L;
            assertDoesNotThrow(() -> validator.checkStorageLimit(tenantId, incomingBytes));
        }

        @Test
        @DisplayName("throws when total exceeds limit")
        void throwsWhenTotalExceedsLimit() {
            mockLimit("maxStorageMB", 1024L);
            when(documentRepository.sumFileSizeBytesByTenantId(tenantId)).thenReturn(900L * 1024L * 1024L);
            when(dicomInstanceRepository.sumFileSizeBytesByTenantId(tenantId)).thenReturn(200L * 1024L * 1024L);

            long incomingBytes = 50L * 1024L * 1024L;
            assertThrows(PlanLimitExceededException.class,
                    () -> validator.checkStorageLimit(tenantId, incomingBytes));
        }

        @Test
        @DisplayName("throws when current alone exceeds limit")
        void throwsWhenCurrentAloneExceedsLimit() {
            mockLimit("maxStorageMB", 1024L);
            when(documentRepository.sumFileSizeBytesByTenantId(tenantId)).thenReturn(1100L * 1024L * 1024L);
            when(dicomInstanceRepository.sumFileSizeBytesByTenantId(tenantId)).thenReturn(0L);

            assertThrows(PlanLimitExceededException.class,
                    () -> validator.checkStorageLimit(tenantId, 0L));
        }

        @Test
        @DisplayName("does not throw when at exact limit")
        void doesNotThrowAtExactLimit() {
            mockLimit("maxStorageMB", 1024L);
            when(documentRepository.sumFileSizeBytesByTenantId(tenantId)).thenReturn(0L);
            when(dicomInstanceRepository.sumFileSizeBytesByTenantId(tenantId)).thenReturn(0L);

            long oneMB = 1024L * 1024L;
            assertDoesNotThrow(() -> validator.checkStorageLimit(tenantId, oneMB));
        }

        @Test
        @DisplayName("throws when 1 byte over limit")
        void throwsWhenOneByteOverLimit() {
            mockLimit("maxStorageMB", 1L);
            when(documentRepository.sumFileSizeBytesByTenantId(tenantId)).thenReturn(0L);
            when(dicomInstanceRepository.sumFileSizeBytesByTenantId(tenantId)).thenReturn(0L);

            long oneMBPlusOne = 1024L * 1024L + 1;
            assertThrows(PlanLimitExceededException.class,
                    () -> validator.checkStorageLimit(tenantId, oneMBPlusOne));
        }

        @Test
        @DisplayName("skips when unlimited (-1)")
        void skipsWhenUnlimited() {
            mockLimit("maxStorageMB", -1L);
            assertDoesNotThrow(() -> validator.checkStorageLimit(tenantId, 999999999L));
        }

        @Test
        @DisplayName("sums document and dicom storage together")
        void sumsDocumentAndDicomStorage() {
            mockLimit("maxStorageMB", 100L);
            when(documentRepository.sumFileSizeBytesByTenantId(tenantId)).thenReturn(60L * 1024L * 1024L);
            when(dicomInstanceRepository.sumFileSizeBytesByTenantId(tenantId)).thenReturn(50L * 1024L * 1024L);

            assertThrows(PlanLimitExceededException.class,
                    () -> validator.checkStorageLimit(tenantId, 0L));
        }
    }

    // ── OCR PAGES ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkOcrPagesLimit")
    class CheckOcrPagesLimitTests {

        @Test
        @DisplayName("does not throw when under limit")
        void doesNotThrowWhenUnderLimit() {
            mockLimit("maxOcrPagesPerMonth", 100L);
            when(ocrMetadataRepository.sumPagesProcessedByTenantIdCurrentMonth(tenantId)).thenReturn(50L);
            assertDoesNotThrow(() -> validator.checkOcrPagesLimit(tenantId));
        }

        @Test
        @DisplayName("throws when at limit")
        void throwsWhenAtLimit() {
            mockLimit("maxOcrPagesPerMonth", 100L);
            when(ocrMetadataRepository.sumPagesProcessedByTenantIdCurrentMonth(tenantId)).thenReturn(100L);

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> validator.checkOcrPagesLimit(tenantId));
            assertEquals("páginas OCR (este mes)", ex.getResourceType());
            assertEquals(100L, ex.getCurrentCount());
        }

        @Test
        @DisplayName("skips when unlimited (-1)")
        void skipsWhenUnlimited() {
            mockLimit("maxOcrPagesPerMonth", -1L);
            assertDoesNotThrow(() -> validator.checkOcrPagesLimit(tenantId));
            verify(ocrMetadataRepository, never()).sumPagesProcessedByTenantIdCurrentMonth(any());
        }
    }

    // ── API CALLS ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkApiCallsLimit")
    class CheckApiCallsLimitTests {

        @Test
        @DisplayName("does not throw when under limit")
        void doesNotThrowWhenUnderLimit() {
            mockLimit("maxApiCallsPerMonth", 500L);
            when(apiCallUsageRepository.getCallCount(eq(tenantId), anyString())).thenReturn(200L);
            assertDoesNotThrow(() -> validator.checkApiCallsLimit(tenantId));
        }

        @Test
        @DisplayName("throws when at limit")
        void throwsWhenAtLimit() {
            mockLimit("maxApiCallsPerMonth", 500L);
            when(apiCallUsageRepository.getCallCount(eq(tenantId), anyString())).thenReturn(500L);

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> validator.checkApiCallsLimit(tenantId));
            assertEquals("llamadas API (este mes)", ex.getResourceType());
            assertEquals(500L, ex.getCurrentCount());
            assertEquals(500L, ex.getMaxLimit());
        }

        @Test
        @DisplayName("throws when over limit")
        void throwsWhenOverLimit() {
            mockLimit("maxApiCallsPerMonth", 500L);
            when(apiCallUsageRepository.getCallCount(eq(tenantId), anyString())).thenReturn(600L);

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> validator.checkApiCallsLimit(tenantId));
            assertEquals(600L, ex.getCurrentCount());
        }

        @Test
        @DisplayName("skips when unlimited (-1)")
        void skipsWhenUnlimited() {
            mockLimit("maxApiCallsPerMonth", -1L);
            assertDoesNotThrow(() -> validator.checkApiCallsLimit(tenantId));
            verify(apiCallUsageRepository, never()).getCallCount(any(), anyString());
        }

        @Test
        @DisplayName("uses current month year_month format")
        void usesCurrentMonthFormat() {
            mockLimit("maxApiCallsPerMonth", 500L);
            when(apiCallUsageRepository.getCallCount(eq(tenantId), anyString())).thenReturn(0L);

            assertDoesNotThrow(() -> validator.checkApiCallsLimit(tenantId));

            var captor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(apiCallUsageRepository).getCallCount(eq(tenantId), captor.capture());
            String yearMonth = captor.getValue();
            assertTrue(yearMonth.matches("\\d{4}-\\d{2}"), "year_month should be yyyy-MM format, got: " + yearMonth);
        }
    }

    // ── TENANT RESOLUTION ────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolvePlanName")
    class ResolvePlanNameTests {

        @Test
        @DisplayName("uses BASIC plan when subscriptionPlan is null")
        void usesBasicWhenPlanNull() {
            tenant.setSubscriptionPlan(null);
            mockLimit("maxUsers", -1L);

            assertDoesNotThrow(() -> validator.checkUsersLimit(tenantId));
            verify(planService).getLimitValue("BASIC", "maxUsers");
        }

        @Test
        @DisplayName("uses PRO plan limits when set")
        void usesProPlanLimits() {
            tenant.setSubscriptionPlan(SubscriptionPlan.PRO);
            mockLimitForPlan(SubscriptionPlan.PRO, "maxUsers", 50L);
            when(userRepository.countByTenantId(tenantId)).thenReturn(20L);

            assertDoesNotThrow(() -> validator.checkUsersLimit(tenantId));
            verify(planService).getLimitValue("PRO", "maxUsers");
        }

        @Test
        @DisplayName("uses ENTERPRISE plan limits when set")
        void usesEnterprisePlanLimits() {
            tenant.setSubscriptionPlan(SubscriptionPlan.ENTERPRISE);
            mockLimitForPlan(SubscriptionPlan.ENTERPRISE, "maxUsers", -1L);

            assertDoesNotThrow(() -> validator.checkUsersLimit(tenantId));
            verify(planService).getLimitValue("ENTERPRISE", "maxUsers");
        }

        @Test
        @DisplayName("throws when tenant not found")
        void throwsWhenTenantNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(tenantRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> validator.checkUsersLimit(unknownId));
        }
    }

    // ── EXCEPTION MESSAGE ────────────────────────────────────────────────────

    @Nested
    @DisplayName("PlanLimitExceededException message")
    class ExceptionMessageTests {

        @Test
        @DisplayName("contains resource type, current, and max in message")
        void messageContainsAllInfo() {
            mockLimit("maxUsers", 5L);
            when(userRepository.countByTenantId(tenantId)).thenReturn(7L);

            PlanLimitExceededException ex = assertThrows(
                    PlanLimitExceededException.class,
                    () -> validator.checkUsersLimit(tenantId));

            String msg = ex.getMessage();
            assertTrue(msg.contains("usuarios"));
            assertTrue(msg.contains("7"));
            assertTrue(msg.contains("5"));
            assertTrue(msg.contains("Límite del plan alcanzado"));
        }
    }

    // ── CHECK REPORT TEMPLATES LIMIT ────────────────────────────────────────────

    @Nested
    @DisplayName("checkReportTemplatesLimit")
    class ReportTemplatesLimitTests {

        @Test
        @DisplayName("allows creation when under limit")
        void allowsWhenUnderLimit() {
            mockLimit("maxReportTemplates", 10L);
            when(reportTemplateRepository.countByTenantId(tenantId)).thenReturn(5L);

            assertDoesNotThrow(() -> validator.checkReportTemplatesLimit(tenantId));
        }

        @Test
        @DisplayName("blocks creation when at limit")
        void blocksWhenAtLimit() {
            mockLimit("maxReportTemplates", 10L);
            when(reportTemplateRepository.countByTenantId(tenantId)).thenReturn(10L);

            assertThrows(PlanLimitExceededException.class,
                    () -> validator.checkReportTemplatesLimit(tenantId));
        }

        @Test
        @DisplayName("allows unlimited for ENTERPRISE")
        void allowsUnlimitedForEnterprise() {
            tenant.setSubscriptionPlan(SubscriptionPlan.ENTERPRISE);
            mockLimitForPlan(SubscriptionPlan.ENTERPRISE, "maxReportTemplates", -1L);

            assertDoesNotThrow(() -> validator.checkReportTemplatesLimit(tenantId));
        }
    }

    // ── CHECK BACKUPS LIMIT ────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkBackupsLimit")
    class BackupsLimitTests {

        @Test
        @DisplayName("allows creation when under limit")
        void allowsWhenUnderLimit() {
            mockLimit("maxBackupsPerYear", 12L);
            when(backupHistoryRepository.countByTenantIdAndYear(eq(tenantId), anyInt())).thenReturn(5L);

            assertDoesNotThrow(() -> validator.checkBackupsLimit(tenantId));
        }

        @Test
        @DisplayName("blocks creation when at limit")
        void blocksWhenAtLimit() {
            mockLimit("maxBackupsPerYear", 12L);
            when(backupHistoryRepository.countByTenantIdAndYear(eq(tenantId), anyInt())).thenReturn(12L);

            assertThrows(PlanLimitExceededException.class,
                    () -> validator.checkBackupsLimit(tenantId));
        }

        @Test
        @DisplayName("allows unlimited for ENTERPRISE")
        void allowsUnlimitedForEnterprise() {
            tenant.setSubscriptionPlan(SubscriptionPlan.ENTERPRISE);
            mockLimitForPlan(SubscriptionPlan.ENTERPRISE, "maxBackupsPerYear", -1L);

            assertDoesNotThrow(() -> validator.checkBackupsLimit(tenantId));
        }
    }
}
