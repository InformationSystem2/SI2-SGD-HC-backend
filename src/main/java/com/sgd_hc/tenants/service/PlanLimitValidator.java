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
import com.sgd_hc.workflow.repository.ReviewTaskRepository;
import com.sgd_hc.documents.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    private final ReviewTaskRepository reviewTaskRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final com.sgd_hc.notifications.repository.NotificationRepository notificationRepository;
    private final org.springframework.beans.factory.ObjectProvider<com.sgd_hc.notifications.service.NotificationService> notificationServiceProvider;

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private void checkAndNotifyLimit(UUID tenantId, String resourceName, long current, long max) {
        if (max <= 0) return;
        double usage = (double) current / max;
        
        // Determinar el umbral más alto alcanzado
        double[] thresholds = { 0.60, 0.70, 0.80, 0.85, 0.90, 0.95, 1.00 };
        double reachedThreshold = 0.0;
        for (double t : thresholds) {
            if (usage >= t) {
                reachedThreshold = t;
            }
        }

        if (reachedThreshold > 0.0) {
            com.sgd_hc.notifications.entity.NotificationType type = com.sgd_hc.notifications.entity.NotificationType.TENANT_STORAGE_LIMIT_ALERT;
            
            // Evitar spam: no enviar más de una alerta para el mismo recurso y umbral en las últimas 24 horas
            int thresholdPercent = (int) (reachedThreshold * 100);
            String thresholdKeyword = String.format("'%s' ha alcanzado el %d%%", resourceName, thresholdPercent);
            
            OffsetDateTime since = OffsetDateTime.now().minusDays(1);
            boolean alreadyNotified = notificationRepository.existsByTenantIdAndTypeAndMessageContainingAndCreatedAtAfter(
                    tenantId, type, thresholdKeyword, since);
            
            if (!alreadyNotified) {
                java.util.List<com.sgd_hc.users.entity.User> members = userRepository.findAllByTenantId(tenantId);
                String severity = reachedThreshold >= 1.0 ? "CRÍTICO" : "ADVERTENCIA";
                String message = String.format("Alerta de límites: el recurso '%s' ha alcanzado el %d%% de su límite planificado. Estado actual: %d/%d utilizados.", 
                        resourceName, thresholdPercent, current, max);
                String title = String.format("Alerta de límite (%s): %s", severity, resourceName);
                
                for (com.sgd_hc.users.entity.User user : members) {
                    boolean isAdmin = user.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getName()));
                    if (isAdmin) {
                        try {
                            notificationServiceProvider.ifAvailable(service -> 
                                service.sendNotificationToUser(user, type, title, message)
                            );
                        } catch (Exception e) {
                            log.warn("No se pudo enviar la notificación de límite al administrador {}: {}", user.getUsername(), e.getMessage());
                        }
                    }
                }
            }
        }
    }

    public void checkUsersLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxUsers");
        if (max == UNLIMITED) return;

        long current = userRepository.countByTenantId(tenantId);
        checkAndNotifyLimit(tenantId, "Usuarios", current + 1, max);
        if (current >= max) {
            throw new PlanLimitExceededException("usuarios", current, max);
        }
    }

    public void checkPatientsLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxPatients");
        if (max == UNLIMITED) return;

        long current = patientRepository.countByTenantId(tenantId);
        checkAndNotifyLimit(tenantId, "Pacientes", current + 1, max);
        if (current >= max) {
            throw new PlanLimitExceededException("pacientes", current, max);
        }
    }

    public void checkDocumentsLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxDocuments");
        if (max == UNLIMITED) return;

        long current = documentRepository.countByTenantId(tenantId);
        checkAndNotifyLimit(tenantId, "Documentos", current + 1, max);
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

        long targetBytes = currentBytes + incomingBytes;
        long targetMB = targetBytes / (1024L * 1024L);
        checkAndNotifyLimit(tenantId, "Almacenamiento", targetMB, maxMB);

        if (targetBytes > maxBytes) {
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

    public void checkActiveReviewTasksLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxActiveReviewTasks");
        if (max == UNLIMITED) return;

        long pending = reviewTaskRepository.countByTenantIdAndStatus(tenantId,
                com.sgd_hc.workflow.entity.ReviewTaskStatus.PENDING);
        long inProgress = reviewTaskRepository.countByTenantIdAndStatus(tenantId,
                com.sgd_hc.workflow.entity.ReviewTaskStatus.IN_PROGRESS);
        long current = pending + inProgress;
        if (current >= max) {
            throw new PlanLimitExceededException("tareas de revisión activas", current, max);
        }
    }

    public void checkReviewTasksMonthlyLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxReviewTasksPerMonth");
        if (max == UNLIMITED) return;

        OffsetDateTime since = OffsetDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long current = reviewTaskRepository.countCreatedSince(tenantId, since);
        if (current >= max) {
            throw new PlanLimitExceededException("tareas de revisión (este mes)", current, max);
        }
    }

    public void checkVersionsPerDocumentLimit(UUID tenantId, UUID documentId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxVersionsPerDocument");
        if (max == UNLIMITED) return;

        long current = documentVersionRepository.countByDocumentIdAndTenantId(documentId, tenantId);
        if (current >= max) {
            throw new PlanLimitExceededException("versiones del documento", current, max);
        }
    }

    public void checkVersionsMonthlyLimit(UUID tenantId) {
        String planName = resolvePlanName(tenantId);
        long max = planService.getLimitValue(planName, "maxVersionsPerMonth");
        if (max == UNLIMITED) return;

        OffsetDateTime since = OffsetDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long current = documentVersionRepository.countByTenantIdSince(tenantId, since);
        if (current >= max) {
            throw new PlanLimitExceededException("versiones de documentos (este mes)", current, max);
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
