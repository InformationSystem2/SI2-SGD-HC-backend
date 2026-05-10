package com.sgd_hc.tenants.mapper;

import com.sgd_hc.tenants.dto.TenantRegisterRequestDto;
import com.sgd_hc.tenants.dto.TenantResponseDto;
import com.sgd_hc.tenants.dto.TenantUpdateDto;
import com.sgd_hc.tenants.entity.SubscriptionPlan;
import com.sgd_hc.tenants.entity.SubscriptionStatus;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.users.entity.Role;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.entity.DocumentType;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class TenantMapper {

    public Tenant toEntity(TenantRegisterRequestDto dto) {
        SubscriptionPlan plan = SubscriptionPlan.BASIC;
        if (dto.selectedPlan() != null) {
            try {
                plan = SubscriptionPlan.valueOf(dto.selectedPlan().toUpperCase());
            } catch (Exception ignored) {}
        }

        return Tenant.builder()
                .name(dto.tenantName())
                .email(dto.adminEmail())
                .phone(dto.adminPhone())
                .subscriptionPlan(plan)
                .subscriptionStatus(SubscriptionStatus.PENDING_PAYMENT)
                .build();
    }

    public Role toAdminRoleEntity(Tenant tenant) {
        return Role.builder()
                .name("ROLE_ADMIN")
                .description("Administrador de la clínica")
                .tenant(tenant)
                .build();
    }

    public User toAdminUserEntity(TenantRegisterRequestDto dto, String encodedPassword, Role adminRole, Tenant tenant) {
        String username = "admin." + tenant.getSlug();
        return User.builder()
                .username(username)
                .email(dto.adminEmail())
                .firstName(dto.adminFirstName())
                .lastName(dto.adminLastName())
                .password(encodedPassword)
                .documentType(DocumentType.valueOf(dto.adminDocumentType().toUpperCase()))
                .documentNumber(dto.adminDocumentNumber())
                .gender(dto.adminGender())
                .isActive(false) // Se activa después del pago
                .roles(new HashSet<>(Set.of(adminRole)))
                .tenant(tenant)
                .build();
    }

    public void updateEntityFromDto(TenantUpdateDto dto, Tenant entity) {
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.email() != null) entity.setEmail(dto.email());
        if (dto.phone() != null) entity.setPhone(dto.phone());
        if (dto.address() != null) entity.setAddress(dto.address());
        if (dto.subscriptionPlan() != null) entity.setSubscriptionPlan(dto.subscriptionPlan());
        if (dto.subscriptionStatus() != null) entity.setSubscriptionStatus(dto.subscriptionStatus());
    }

    public TenantResponseDto toResponseDto(Tenant entity) {
        if (entity == null) {
            return null;
        }
        
        return new TenantResponseDto(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getAddress(),
                entity.getSubscriptionPlan(),
                entity.getSubscriptionStatus(),
                entity.getSubscriptionStartDate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
