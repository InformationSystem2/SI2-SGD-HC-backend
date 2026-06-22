package com.sgd_hc.tenants.service;

import com.sgd_hc.security.exception.PlanFeatureDisabledException;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanFeatureValidator {

    private final TenantRepository tenantRepository;
    private final PlanService planService;

    public void checkDicomImaging(UUID tenantId) {
        if (!isFeatureEnabled(tenantId, "dicom_imaging")) {
            throw new PlanFeatureDisabledException("imágenes DICOM", resolvePlanName(tenantId));
        }
    }

    public void checkOcrScanning(UUID tenantId) {
        if (!isFeatureEnabled(tenantId, "ocr_scanning")) {
            throw new PlanFeatureDisabledException("escaneo OCR", resolvePlanName(tenantId));
        }
    }

    public void checkOnlineEditing(UUID tenantId) {
        if (!isFeatureEnabled(tenantId, "online_editing")) {
            throw new PlanFeatureDisabledException("edición en línea", resolvePlanName(tenantId));
        }
    }

    public void checkCustomBranding(UUID tenantId) {
        if (!isFeatureEnabled(tenantId, "custom_branding")) {
            throw new PlanFeatureDisabledException("branding personalizado", resolvePlanName(tenantId));
        }
    }

    public void checkAdvancedAnalytics(UUID tenantId) {
        if (!isFeatureEnabled(tenantId, "advanced_analytics")) {
            throw new PlanFeatureDisabledException("analíticas avanzadas", resolvePlanName(tenantId));
        }
    }

    public void checkReportBuilder(UUID tenantId) {
        if (!isFeatureEnabled(tenantId, "report_builder")) {
            throw new PlanFeatureDisabledException("constructor de reportes", resolvePlanName(tenantId));
        }
    }

    public void checkApiAccess(UUID tenantId) {
        if (!isFeatureEnabled(tenantId, "api_access")) {
            throw new PlanFeatureDisabledException("acceso API", resolvePlanName(tenantId));
        }
    }

    public void checkPushNotifications(UUID tenantId) {
        if (!isFeatureEnabled(tenantId, "push_notifications")) {
            throw new PlanFeatureDisabledException("notificaciones push", resolvePlanName(tenantId));
        }
    }

    public void checkEmailNotifications(UUID tenantId) {
        if (!isFeatureEnabled(tenantId, "email_notifications")) {
            throw new PlanFeatureDisabledException("notificaciones por email", resolvePlanName(tenantId));
        }
    }

    public void checkCustomRoles(UUID tenantId) {
        if (!isFeatureEnabled(tenantId, "custom_roles")) {
            throw new PlanFeatureDisabledException("roles personalizados", resolvePlanName(tenantId));
        }
    }

    private boolean isFeatureEnabled(UUID tenantId, String featureKey) {
        String planName = resolvePlanName(tenantId);
        return planService.isFeatureEnabled(planName, featureKey);
    }

    private String resolvePlanName(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + tenantId));
        return tenant.getSubscriptionPlan() != null
                ? tenant.getSubscriptionPlan().name()
                : "BASIC";
    }
}
