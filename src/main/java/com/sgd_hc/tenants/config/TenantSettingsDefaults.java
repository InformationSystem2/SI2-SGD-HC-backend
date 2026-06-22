package com.sgd_hc.tenants.config;

import java.util.Map;
import java.util.HashMap;

public final class TenantSettingsDefaults {

    private TenantSettingsDefaults() {}

    public static final Map<String, Object> LIMITS = Map.ofEntries(
            Map.entry("maxUsers", 0),
            Map.entry("maxStorageMB", 0),
            Map.entry("maxApiCallsPerMonth", 0),
            Map.entry("maxPatients", 0),
            Map.entry("maxDocuments", 0),
            Map.entry("maxDocumentTemplates", 0),
            Map.entry("maxReportTemplates", 0),
            Map.entry("maxDicomStudies", 0),
            Map.entry("maxOcrPagesPerMonth", 0),
            Map.entry("maxBackupsPerYear", 0),
            Map.entry("maxStaffRoles", 0),
            Map.entry("maxActiveReviewTasks", 0),
            Map.entry("maxReviewTasksPerMonth", 0),
            Map.entry("maxVersionsPerDocument", 0),
            Map.entry("maxVersionsPerMonth", 0)
    );

    public static final Map<String, Object> REGIONAL = Map.of(
            "timezone", "America/Lima",
            "locale", "es-PE",
            "dateFormat", "DD/MM/YYYY",
            "currency", "PEN"
    );

    public static final Map<String, Object> NOTIFICATIONS = Map.of(
            "emailEnabled", true,
            "smsEnabled", false,
            "pushEnabled", true
    );

    public static final Map<String, Object> SECURITY = Map.of(
            "sessionTimeoutMinutes", 30,
            "passwordExpiryDays", 90,
            "require2FA", false
    );

    public static Map<String, Object> getAllDefaults() {
        return Map.of(
                "limits", LIMITS,
                "regional", REGIONAL,
                "notifications", NOTIFICATIONS,
                "security", SECURITY
        );
    }

    public static Map<String, Object> mergeWithDefaults(Map<String, Object> existing) {
        if (existing == null) return getAllDefaults();

        Map<String, Object> result = new HashMap<>(getAllDefaults());

        existing.forEach((key, value) -> {
            if (value instanceof Map) {
                Map<String, Object> nested = new HashMap<>();
                nested.putAll(result.get(key) != null ? (Map<String, Object>) result.get(key) : Map.of());
                nested.putAll((Map<String, Object>) value);
                result.put(key, nested);
            } else {
                result.put(key, value);
            }
        });

        return result;
    }
}