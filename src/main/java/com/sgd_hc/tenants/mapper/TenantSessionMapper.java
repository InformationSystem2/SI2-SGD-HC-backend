package com.sgd_hc.tenants.mapper;

import com.sgd_hc.tenants.service.TenantSessionService.SessionData;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TenantSessionMapper {

    public Map<String, Object> toAuditMap(SessionData entity) {
        if (entity == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("plan", entity.getPlan());
        map.put("expiresAt", entity.getExpiresAt() != null ? entity.getExpiresAt().toString() : null);
        if (entity.getRegistrationData() != null) {
            map.put("tenantName", entity.getRegistrationData().getTenantName());
            map.put("adminEmail", entity.getRegistrationData().getAdminEmail());
        }
        return map;
    }
}
