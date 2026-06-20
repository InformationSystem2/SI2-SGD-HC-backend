package com.sgd_hc.audit.util;

import java.util.*;

public final class AuditoriaUtils {

    private AuditoriaUtils() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object>[] calculateDiff(Map<String, Object> antes, Map<String, Object> despues) {
        if (antes == null && despues == null) {
            return new Map[]{Collections.emptyMap(), Collections.emptyMap()};
        }

        Map<String, Object> diffAntes = new LinkedHashMap<>();
        Map<String, Object> diffDespues = new LinkedHashMap<>();

        if (antes == null) {
            diffDespues.putAll(despues);
            return new Map[]{diffAntes, diffDespues};
        }

        if (despues == null) {
            diffAntes.putAll(antes);
            return new Map[]{diffAntes, diffDespues};
        }

        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(antes.keySet());
        allKeys.addAll(despues.keySet());

        for (String key : allKeys) {
            Object valAntes = antes.get(key);
            Object valDespues = despues.get(key);

            if (!Objects.equals(valAntes, valDespues)) {
                diffAntes.put(key, valAntes);
                diffDespues.put(key, valDespues);
            }
        }

        if (diffAntes.isEmpty() && diffDespues.isEmpty()) {
            return new Map[]{antes, despues};
        }

        return new Map[]{diffAntes, diffDespues};
    }

    public static Map<String, Object> sanitizeMap(Map<String, Object> data) {
        if (data == null) return null;
        Map<String, Object> sanitized = new HashMap<>(data);
        sanitized.computeIfPresent("password", (k, v) -> "***ENMASCARADO***");
        sanitized.computeIfPresent("passwordConfirm", (k, v) -> "***ENMASCARADO***");
        sanitized.computeIfPresent("currentPassword", (k, v) -> "***ENMASCARADO***");
        sanitized.computeIfPresent("newPassword", (k, v) -> "***ENMASCARADO***");
        for (Map.Entry<String, Object> entry : sanitized.entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) entry.getValue();
                sanitized.put(entry.getKey(), sanitizeMap(nested));
            }
        }
        return sanitized;
    }
}
