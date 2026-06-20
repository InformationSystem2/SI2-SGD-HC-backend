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
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.ApiCallUsageRepository;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.users.repository.RoleRepository;
import com.sgd_hc.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanLimitValidator {

    private static final long UNLIMITED = -1L;

    private final TenantRepository tenantRepository;
    private final PlanService planService;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DocumentRepository documentRepository;
    private final DocumentTemplateRepository documentTemplateRepository;
    private final DicomStudyRepository dicomStudyRepository;
    private final RoleRepository roleRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final DocumentOcrMetadataRepository ocrMetadataRepository;
    private final ApiCallUsageRepository apiCallUsageRepository;
    private final ReportTemplateRepository reportTemplateRepository;
    private final BackupHistoryRepository backupHistoryRepository;

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    public void checkUsersLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxUsers");
        if (max == UNLIMITED) return;

        long current = userRepository.countByTenantId(tenantId);
        if (current >= max) {
            throw new PlanLimitExceededException("usuarios", current, max);
        }
    }

    public void checkPatientsLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxPatients");
        if (max == UNLIMITED) return;

        long current = patientRepository.countByTenantId(tenantId);
        if (current >= max) {
            throw new PlanLimitExceededException("pacientes", current, max);
        }
    }

    public void checkDocumentsLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxDocuments");
        if (max == UNLIMITED) return;

        long current = documentRepository.countByTenantId(tenantId);
        if (current >= max) {
            throw new PlanLimitExceededException("documentos", current, max);
        }
    }

    public void checkDocumentTemplatesLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxDocumentTemplates");
        if (max == UNLIMITED) return;

        long current = documentTemplateRepository.countByTenantId(tenantId);
        if (current >= max) {
            throw new PlanLimitExceededException("plantillas", current, max);
        }
    }

    public void checkDicomStudiesLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxDicomStudies");
        if (max == UNLIMITED) return;

        long current = dicomStudyRepository.countByTenantId(tenantId);
        if (current >= max) {
            throw new PlanLimitExceededException("estudios DICOM", current, max);
        }
    }

    public void checkStaffRolesLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxStaffRoles");
        if (max == UNLIMITED) return;

        long current = roleRepository.countByTenantId(tenantId);
        if (current >= max) {
            throw new PlanLimitExceededException("roles de staff", current, max);
        }
    }

    public void checkStorageLimit(UUID tenantId, long incomingBytes) {
        String planName = resolvePlanName(tenantId);
        long maxMB = planService.getLimitValue(planName, "maxStorageMB");
        if (maxMB == UNLIMITED) return;

        long maxBytes = maxMB * 1024L * 1024L;
        long docBytes = documentRepository.sumFileSizeBytesByTenantId(tenantId);
        long dicomBytes = dicomInstanceRepository.sumFileSizeBytesByTenantId(tenantId);
        long currentBytes = docBytes + dicomBytes;

        if (currentBytes + incomingBytes > maxBytes) {
            long currentMB = currentBytes / (1024L * 1024L);
            throw new PlanLimitExceededException("almacenamiento", currentMB, maxMB);
        }
    }

    public void checkOcrPagesLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxOcrPagesPerMonth");
        if (max == UNLIMITED) return;

        long current = ocrMetadataRepository.sumPagesProcessedByTenantIdCurrentMonth(tenantId);
        if (current >= max) {
            throw new PlanLimitExceededException("páginas OCR (este mes)", current, max);
        }
    }

    public void checkApiCallsLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxApiCallsPerMonth");
        if (max == UNLIMITED) return;

        String yearMonth = LocalDate.now().format(YEAR_MONTH_FORMAT);
        long current = apiCallUsageRepository.getCallCount(tenantId, yearMonth);
        if (current >= max) {
            throw new PlanLimitExceededException("llamadas API (este mes)", current, max);
        }
    }

    public void checkReportTemplatesLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxReportTemplates");
        if (max == UNLIMITED) return;

        long current = reportTemplateRepository.countByTenantId(tenantId);
        if (current >= max) {
            throw new PlanLimitExceededException("plantillas de reporte", current, max);
        }
    }

    public void checkBackupsLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxBackupsPerYear");
        if (max == UNLIMITED) return;

        int currentYear = LocalDate.now().getYear();
        long current = backupHistoryRepository.countByTenantIdAndYear(tenantId, currentYear);
        if (current >= max) {
            throw new PlanLimitExceededException("backups (este año)", current, max);
        }
    }

    private String resolvePlanName(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + tenantId));
        return tenant.getSubscriptionPlan() != null
                ? tenant.getSubscriptionPlan().name()
                : "BASIC";
    }
}
