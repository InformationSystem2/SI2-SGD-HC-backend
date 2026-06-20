package com.sgd_hc.audit.service;

import java.util.Map;

public interface AuditableService<ID, E> {

    E getEntity(ID id);

    Map<String, Object> toAuditMap(E entity);

    default Map<String, Object> toAuditMapFromResult(Object result) {
        return Map.of();
    }
}
