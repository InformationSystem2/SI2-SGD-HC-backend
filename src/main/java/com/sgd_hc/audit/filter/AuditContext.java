package com.sgd_hc.audit.filter;

public final class AuditContext {

    private static final ThreadLocal<Boolean> ASPECT_ACTIVE = ThreadLocal.withInitial(() -> false);

    private AuditContext() {}

    public static void setAspectActive(boolean active) {
        ASPECT_ACTIVE.set(active);
    }

    public static boolean isAspectActive() {
        return ASPECT_ACTIVE.get();
    }

    public static void clear() {
        ASPECT_ACTIVE.remove();
    }
}
