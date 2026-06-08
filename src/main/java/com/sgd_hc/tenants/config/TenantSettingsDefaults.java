package com.sgd_hc.tenants.config;

import java.util.Map;
import java.util.HashMap;

public final class TenantSettingsDefaults {

    private TenantSettingsDefaults() {}

    public static final Map<String, Object> LIMITS = Map.of(
            "maxUsers", 100,
            "maxStorageMB", 5000,
            "maxApiCallsPerMonth", 10000
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
            "pushEnabled", false
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