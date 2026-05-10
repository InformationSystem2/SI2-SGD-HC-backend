package com.sgd_hc.security.config.tenant;

import java.util.UUID;

public class TenantContext {
    private static final ThreadLocal<UUID> CURRENT_TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TENANT_SLUG = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BYPASS_FILTER = ThreadLocal.withInitial(() -> false);

    public static void setCurrentTenantId(UUID tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }

    public static UUID getCurrentTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    public static void setCurrentTenantSlug(String slug) {
        CURRENT_TENANT_SLUG.set(slug);
    }

    public static String getCurrentTenantSlug() {
        return CURRENT_TENANT_SLUG.get();
    }

    public static void setBypassFilter(boolean bypass) {
        BYPASS_FILTER.set(bypass);
    }

    public static boolean isBypassFilter() {
        return BYPASS_FILTER.get();
    }

    public static void clear() {
        CURRENT_TENANT_ID.remove();
        CURRENT_TENANT_SLUG.remove();
        BYPASS_FILTER.remove();
    }
}
