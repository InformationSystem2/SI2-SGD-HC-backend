package com.sgd_hc.security.exception;

public class TenantSuspendedException extends RuntimeException {

    private final String tenantSlug;
    private final String tenantName;

    public TenantSuspendedException(String tenantSlug, String tenantName) {
        super("El tenant '" + tenantName + "' está suspendido. Contacte al administrador.");
        this.tenantSlug = tenantSlug;
        this.tenantName = tenantName;
    }

    public String getTenantSlug() {
        return tenantSlug;
    }

    public String getTenantName() {
        return tenantName;
    }
}