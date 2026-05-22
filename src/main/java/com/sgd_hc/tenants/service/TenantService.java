package com.sgd_hc.tenants.service;

import com.sgd_hc.tenants.dto.TenantPaymentRequestDto;
import com.sgd_hc.tenants.dto.TenantRegisterRequestDto;
import com.sgd_hc.tenants.dto.TenantResponseDto;
import com.sgd_hc.tenants.dto.TenantUpdateDto;
import com.sgd_hc.tenants.entity.*;
import com.sgd_hc.tenants.mapper.TenantMapper;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.tenants.utils.TagSlugGenerator;
import com.sgd_hc.users.entity.Role;
import com.sgd_hc.users.entity.User;

import com.sgd_hc.users.repository.RoleRepository;
import com.sgd_hc.users.repository.UserRepository;
import com.sgd_hc.documents.TemplateDataSeeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.sgd_hc.security.config.tenant.TenantContext;

/**
 * Servicio central para la gestión de Tenants.
 * Incluye lógica de onboarding (público) y gestión administrativa (superadmin).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;
    private final TenantMapper tenantMapper;
    private final TemplateDataSeeder templateDataSeeder;

    @Value("${app.seed.system.slug}")
    private String systemSlug;

    // ── GESTIÓN ADMINISTRATIVA (Superadmin) ──────────────────────────────────

    @Transactional(readOnly = true)
    public List<TenantResponseDto> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(tenantMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantResponseDto getTenantById(UUID id) {
        return tenantMapper.toResponseDto(findOrThrow(id));
    }

    @Transactional
    public TenantResponseDto updateTenant(UUID id, TenantUpdateDto dto) {
        Tenant tenant = findOrThrow(id);
        tenantMapper.updateEntityFromDto(dto, tenant);
        return tenantMapper.toResponseDto(tenantRepository.save(tenant));
    }

    // ── PÚBLICO (Registro y Pago) ─────────────────────────────────

    @Transactional
    public Map<String, Object> startRegistration(TenantRegisterRequestDto dto) {
        // Activar bypass para permitir verificar slugs globalmente
        TenantContext.setBypassFilter(true);

        try {
            // Generar slug técnico (tagslug) automáticamente a partir del nombre
            String baseSlug = TagSlugGenerator.generateTenantSlug(dto.tenantName());
            String finalSlug = baseSlug;
            int count = 1;

            // Bucle de resolución de colisiones
            while (tenantRepository.findBySlug(finalSlug).isPresent()) {
                finalSlug = baseSlug + count;
                count++;
            }

            Tenant tenant = tenantMapper.toEntity(dto);
            tenant.setSlug(finalSlug); // Sobrescribir con el slug técnico generado
            tenant = tenantRepository.save(tenant);

            Tenant systemTenant = tenantRepository.findBySlug(systemSlug)
                    .orElseThrow(() -> new IllegalStateException("Tenant maestro no encontrado."));
            
            // Busca el rol ADMIN dentro del tenant superuser
            Role adminRole = roleRepository.findByNameAndTenantId("ROLE_ADMIN", systemTenant.getId())
                    .orElseThrow(() -> new IllegalStateException("Rol global ADMIN no encontrado."));

            String encodedPassword = passwordEncoder.encode(dto.adminPassword());
            User adminUser = userRepository.save(
                tenantMapper.toAdminUserEntity(dto, encodedPassword, adminRole, tenant));

            // Sembrar plantillas clínicas predefinidas para el nuevo tenant
            templateDataSeeder.seedForTenant(tenant);

            return Map.of(
                    "tenantId", tenant.getId().toString(),
                    "adminUsername", adminUser.getUsername(),
                    "status", "PENDING_PAYMENT",
                    "message", "Registro inicial exitoso. Proceda al pago simulado."
            );


        } finally {
            TenantContext.setBypassFilter(false);
        }
    }

    @Transactional
    public Map<String, Object> processPayment(TenantPaymentRequestDto dto) {
        // Activar bypass para permitir encontrar el admin de cualquier clínica pendiente
        TenantContext.setBypassFilter(true);

        try {
            Tenant tenant = findOrThrow(dto.tenantId());

            if (tenant.getSubscriptionStatus() == SubscriptionStatus.ACTIVE) {
                throw new IllegalStateException("El tenant ya está activo.");
            }

            tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
            tenant.setSubscriptionStartDate(LocalDate.now());
            tenantRepository.save(tenant);

            // Filtra por tenant_id porque el filtro se apaga temporalmente
            User admin = userRepository.findAllByTenantId(tenant.getId()).stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No se encontró usuario admin."));
            
            admin.setIsActive(true);
            userRepository.save(admin);

            return Map.of(
                    "tenantSlug", tenant.getSlug(),
                    "status", "ACTIVE",
                    "message", "Pago exitoso. Ya puede iniciar sesión."
            );
        } finally {
            TenantContext.setBypassFilter(false);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkSlug(String slug) {
        boolean exists = tenantRepository.findBySlug(slug).isPresent();
        return Map.of("slug", slug, "available", !exists);
    }

    // ── HELPERS ─────────────────────────────────────────────────────────────
    private Tenant findOrThrow(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + id));
    }
}
