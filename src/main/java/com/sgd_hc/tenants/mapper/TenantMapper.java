package com.sgd_hc.tenants.mapper;

import com.sgd_hc.tenants.dto.TenantRegisterRequestDto;
import com.sgd_hc.tenants.dto.TenantRegistrationDataDto;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.users.entity.Role;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.entity.DocumentType;
import com.sgd_hc.tenants.entity.SubscriptionPlan;
import com.sgd_hc.tenants.entity.SubscriptionStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
public class TenantMapper {

    public TenantRegistrationDataDto toRegistrationData(TenantRegisterRequestDto dto, String encodedPassword) {
        return new TenantRegistrationDataDto(
                dto.tenantName(),
                dto.adminFirstName(),
                dto.adminLastName(),
                dto.adminEmail(),
                encodedPassword,
                dto.adminPhone(),
                dto.adminDocumentType(),
                dto.adminDocumentNumber(),
                dto.adminGender()
        );
    }

    public Tenant toEntity(TenantRegistrationDataDto dto, String finalSlug, SubscriptionPlan plan) {
        return Tenant.builder()
                .name(dto.getTenantName())
                .slug(finalSlug)
                .email(dto.getAdminEmail())
                .phone(dto.getAdminPhone())
                .subscriptionPlan(plan)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionStartDate(LocalDate.now())
                .billingCycle("MONTHLY")
                .build();
    }

    public Role toAdminRoleEntity(Tenant tenant) {
        return Role.builder()
                .name("ROLE_ADMIN")
                .description("Administrador de la clínica")
                .tenant(tenant)
                .build();
    }

    public User toAdminUserEntity(TenantRegistrationDataDto dto, Role adminRole, Tenant tenant) {
        String username = "admin." + tenant.getSlug();
        return User.builder()
                .username(username)
                .email(dto.getAdminEmail())
                .firstName(dto.getAdminFirstName())
                .lastName(dto.getAdminLastName())
                .password(dto.getAdminPassword())
                .documentType(DocumentType.valueOf(dto.getAdminDocumentType().toUpperCase()))
                .documentNumber(dto.getAdminDocumentNumber())
                .gender(dto.getAdminGender())
                .isActive(true)
                .roles(new HashSet<>(Set.of(adminRole)))
                .tenant(tenant)
                .build();
    }


    public Map<String, Object> toAuditMap(Tenant entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId().toString());
        map.put("name", entity.getName());
        map.put("slug", entity.getSlug());
        map.put("email", entity.getEmail());
        map.put("phone", entity.getPhone());
        map.put("address", entity.getAddress());
        map.put("subscriptionPlan", entity.getSubscriptionPlan());
        map.put("subscriptionStatus", entity.getSubscriptionStatus());
        map.put("settings", entity.getSettings());
        map.put("logoUrl", entity.getLogoUrl());
        map.put("subscriptionStartDate", entity.getSubscriptionStartDate() != null ? entity.getSubscriptionStartDate().toString() : null);
        map.put("subscriptionEndDate", entity.getSubscriptionEndDate() != null ? entity.getSubscriptionEndDate().toString() : null);
        map.put("billingCycle", entity.getBillingCycle());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }

}
