package com.sgd_hc.tenants.service;

import com.sgd_hc.tenants.dto.*;
import com.sgd_hc.tenants.entity.*;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.tenants.utils.TagSlugGenerator;
import com.sgd_hc.users.entity.Role;
import com.sgd_hc.users.entity.User;

import com.sgd_hc.users.repository.RoleRepository;
import com.sgd_hc.users.repository.UserRepository;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.documents.repository.DocumentTemplateRepository;
import com.sgd_hc.patients.repository.PatientRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.stream.Collectors;
import static com.sgd_hc.security.utils.SecurityUtils.*;

import com.sgd_hc.tenants.config.TenantSettingsDefaults;
import com.sgd_hc.security.config.tenant.TenantContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private final DocumentRepository documentRepository;
    private final DocumentTemplateRepository documentTemplateRepository;
    private final PatientRepository patientRepository;
    private final TenantSessionService sessionService;
    private final TenantRevocationService revocationService;
    private final ObjectMapper objectMapper;

    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.system.slug}")
    private String systemSlug;

    // ── PÚBLICO (Registro y Pago) ─────────────────────────────────

    @Transactional
    public Map<String, Object> startRegistration(TenantRegisterRequestDto dto) {
        var sessionOpt = sessionService.getSession(dto.sessionToken());
        if (sessionOpt.isEmpty()) {
            throw new IllegalArgumentException("Sesión de registro expirada o inválida. Por favor, inicie nuevamente.");
        }

        var sessionData = sessionOpt.get();

        TenantSessionService.RegistrationData regData = new TenantSessionService.RegistrationData(
                dto.tenantName(),
                dto.adminFirstName(),
                dto.adminLastName(),
                dto.adminEmail(),
                dto.adminPassword(),
                dto.adminPhone(),
                dto.adminDocumentType(),
                dto.adminDocumentNumber(),
                dto.adminGender()
        );

        sessionService.saveRegistrationData(dto.sessionToken(), regData);

        return Map.of(
                "status", "REGISTRATION_SAVED",
                "message", "Datos guardados. Proceda al pago."
        );
    }

    @Transactional
    public TenantSessionResponseDto initSession(TenantInitSessionDto dto) {
        String token = sessionService.createSession(dto.selectedPlan());

        return new TenantSessionResponseDto(
                token,
                "Sesión iniciada. Proceda a registrar los datos de su clínica."
        );
    }

    @Transactional
    public Map<String, Object> processPayment(TenantPaymentRequestDto dto) {
        var sessionOpt = sessionService.getSession(dto.sessionToken());
        if (sessionOpt.isEmpty()) {
            throw new IllegalArgumentException("Sesión de registro expirada. Por favor, inicie nuevamente.");
        }

        var sessionData = sessionOpt.get();
        var regData = sessionData.getRegistrationData();
        if (regData == null) {
            throw new IllegalStateException("Datos de registro no encontrados. Por favor, complete el formulario.");
        }

        TenantContext.setBypassFilter(true);

        try {
            Tenant tenant = createTenantWithAdmin(regData, sessionData.getPlan());
            sessionService.removeSession(dto.sessionToken());

            return Map.of(
                    "tenantSlug", tenant.getSlug(),
                    "adminUsername", "admin." + tenant.getSlug(),
                    "status", "ACTIVE",
                    "message", "Pago exitoso. Ya puede iniciar sesión."
            );
        } finally {
            TenantContext.setBypassFilter(false);
        }
    }

    private String generateUniqueSlug(String baseName) {
        String baseSlug = TagSlugGenerator.generateTenantSlug(baseName);
        String slug = baseSlug;
        int count = 1;
        while (tenantRepository.findBySlug(slug).isPresent()) {
            slug = baseSlug + count;
            count++;
        }
        return slug;
    }

    private Tenant createTenantWithAdmin(TenantSessionService.RegistrationData regData, String plan) {
        String finalSlug = generateUniqueSlug(regData.getTenantName());

        Tenant tenant = Tenant.builder()
                .name(regData.getTenantName())
                .slug(finalSlug)
                .email(regData.getAdminEmail())
                .phone(regData.getAdminPhone())
                .subscriptionPlan(SubscriptionPlan.valueOf(plan.toUpperCase()))
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionStartDate(LocalDate.now())
                .settings(buildDefaultSettings())
                .build();
        tenant = tenantRepository.save(tenant);

        Tenant systemTenant = tenantRepository.findBySlug(systemSlug)
                .orElseThrow(() -> new IllegalStateException("Tenant maestro no encontrado."));

        Role adminRole = roleRepository.findByNameAndTenantId("ROLE_ADMIN", systemTenant.getId())
                .orElseThrow(() -> new IllegalStateException("Rol global ADMIN no encontrado."));

        User admin = User.builder()
                .username("admin." + tenant.getSlug())
                .email(regData.getAdminEmail())
                .firstName(regData.getAdminFirstName())
                .lastName(regData.getAdminLastName())
                .password(passwordEncoder.encode(regData.getAdminPassword()))
                .documentType(com.sgd_hc.users.entity.DocumentType.valueOf(regData.getAdminDocumentType().toUpperCase()))
                .documentNumber(regData.getAdminDocumentNumber())
                .gender(regData.getAdminGender())
                .isActive(true)
                .roles(new java.util.HashSet<>(Set.of(adminRole)))
                .tenant(tenant)
                .build();
        userRepository.save(admin);

        return tenant;
    }

    private Map<String, Object> buildDefaultSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.putAll(TenantSettingsDefaults.getAllDefaults());
        try {
            ClassPathResource res = new ClassPathResource("default-branding.json");
            Map<String, Object> defaultBranding = objectMapper.readValue(res.getInputStream(), Map.class);
            settings.put("branding", defaultBranding);
        } catch (IOException e) {
            log.warn("No se pudo cargar default-branding.json, usando defaults de ajustes.");
        }
        return settings;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkSlug(String slug) {
        boolean exists = tenantRepository.findBySlug(slug).isPresent();
        return Map.of("slug", slug, "available", !exists);
    }

    // ── INFORMACIÓN BÁSICA DEL TENANT (por slug) ────────────────────────────

    @Transactional(readOnly = true)
    public TenantInfoDto getTenantInfoBySlug(String slug) {
        Set<String> authorities = currentAuthorities();
        Tenant tenant = findTenantBySlugOrThrow(slug);

        AdminInfoDto adminInfo = extractAdminInfo(slug);

        return mapToTenantInfoDto(tenant, adminInfo, authorities);
    }

    @Transactional
    public TenantInfoDto updateTenantBasicInfo(String slug, Map<String, Object> data) {
        Set<String> authorities = currentAuthorities();
        validateUpdateBasicInfoPermissions(data, authorities);

        Tenant tenant = findTenantBySlugOrThrow(slug);

        if (data.containsKey("name") && data.get("name") != null) {
            tenant.setName((String) data.get("name"));
        }
        if (data.containsKey("email") && data.get("email") != null) {
            tenant.setEmail((String) data.get("email"));
        }
        if (data.containsKey("phone") && data.get("phone") != null) {
            tenant.setPhone((String) data.get("phone"));
        }
        if (data.containsKey("address") && data.get("address") != null) {
            tenant.setAddress((String) data.get("address"));
        }
        if (data.containsKey("logoUrl")) {
            tenant.setLogoUrl((String) data.get("logoUrl"));
        }

        tenant = tenantRepository.save(tenant);

        AdminInfoDto adminInfo = extractAdminInfo(slug);

        return mapToTenantInfoDto(tenant, adminInfo, authorities);
    }

    // ── SETTINGS (por slug) ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getSettingsBySlug(String slug) {
        requireAuthority(currentAuthorities(), "tenant:read:settings");
        Tenant tenant = findTenantBySlugOrThrow(slug);
        return getSettingsFromTenant(tenant);
    }

    @Transactional
    public Map<String, Object> updateSettingsBySlug(String slug, Map<String, Object> newSettings) {
        requireAuthority(currentAuthorities(), "tenant:update:settings");
        Tenant tenant = findTenantBySlugOrThrow(slug);
        return updateSettingsOnTenant(tenant, newSettings);
    }

    public TenantStatsDto getTenantStats(String slug) {
        Tenant tenant = findTenantBySlugOrThrow(slug);

        int userCount = userRepository.findAllByTenantId(tenant.getId()).size();
        Map<String, Object> settings = getSettingsFromTenant(tenant);
        PlanLimits limits = extractPlanLimits(settings);

        return new TenantStatsDto(
                userCount,
                limits.maxUsers(),
                0L,
                limits.maxStorageMB(),
                0L,
                limits.maxApiCalls()
        );
    }

    // ── HELPERS ─────────────────────────────────────────────────────────────

    private Tenant findTenantBySlugOrThrow(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalStateException("Slug de tenant no proporcionado");
        }
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + slug));
    }

    private AdminInfoDto extractAdminInfo(String slug) {
        User admin = userRepository.findByTenantSlugWithRole(slug, "ROLE_ADMIN");
        if (admin == null) {
            return null;
        }
        return new AdminInfoDto(
                admin.getId(),
                admin.getUsername(),
                admin.getFirstName(),
                admin.getLastName(),
                admin.getEmail(),
                admin.getPhone()
        );
    }

    private PlanLimits extractPlanLimits(Map<String, Object> settings) {
        @SuppressWarnings("unchecked")
        Map<String, Object> limits = (Map<String, Object>) settings.getOrDefault("limits", new HashMap<>());
        return new PlanLimits(
                ((Number) limits.getOrDefault("maxUsers", 10)).intValue(),
                ((Number) limits.getOrDefault("maxStorageMB", 5120L)).longValue(),
                ((Number) limits.getOrDefault("maxApiCallsPerMonth", 50000L)).longValue()
        );
    }

    private record PlanLimits(int maxUsers, long maxStorageMB, long maxApiCalls) {}

    private Map<String, Object> getSettingsFromTenant(Tenant tenant) {
        Map<String, Object> settings = tenant.getSettings();
        if (settings == null || settings.isEmpty()) {
            return TenantSettingsDefaults.getAllDefaults();
        }
        return TenantSettingsDefaults.mergeWithDefaults(settings);
    }

    private Map<String, Object> updateSettingsOnTenant(Tenant tenant, Map<String, Object> newSettings) {
        Map<String, Object> currentSettings = tenant.getSettings();
        if (currentSettings == null) {
            currentSettings = new HashMap<>();
        } else {
            currentSettings = new HashMap<>(currentSettings);
        }

        if (newSettings.containsKey("limits")) {
            currentSettings.put("limits", newSettings.get("limits"));
        }
        if (newSettings.containsKey("regional")) {
            currentSettings.put("regional", newSettings.get("regional"));
        }
        if (newSettings.containsKey("notifications")) {
            currentSettings.put("notifications", newSettings.get("notifications"));
        }
        if (newSettings.containsKey("security")) {
            currentSettings.put("security", newSettings.get("security"));
        }

        tenant.setSettings(currentSettings);
        tenantRepository.save(tenant);

        return getSettingsFromTenant(tenant);
    }

    // ── HELPERS ─────────────────────────────────────────────────────────────
    private Tenant findOrThrow(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + id));
    }

    // ── GESTIÓN ADMINISTRATIVA PARA SUPERADMIN (HU-17) ─────────────────────

    @Transactional(readOnly = true)
    public PageResponseDto<TenantListItemDto> getTenantsPaged(
            int page,
            int size,
            String search
    ) {
        Set<String> authorities = currentAuthorities();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Tenant> tenantPage;
        boolean hasSearch = search != null && !search.isBlank();

        if (hasSearch) {
            tenantPage = tenantRepository.findAllSearch(search, pageable);
        } else {
            tenantPage = tenantRepository.findAll(pageable);
        }

        List<TenantListItemDto> items = tenantPage.getContent().stream()
                .map(tenant -> mapToTenantListItemDto(tenant, authorities))
                .toList();

        return new PageResponseDto<>(
                items,
                tenantPage.getNumber(),
                tenantPage.getSize(),
                tenantPage.getTotalElements(),
                tenantPage.getTotalPages(),
                tenantPage.isFirst(),
                tenantPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public TenantDetailDto getTenantDetails(UUID id) {
        Set<String> authorities = currentAuthorities();
        Tenant tenant = findOrThrow(id);

        AdminInfoDto adminInfo = extractAdminInfo(tenant.getSlug());

        int userCount = tenantRepository.countActiveUsersByTenantId(tenant.getId());
        Map<String, Object> settings = getSettingsFromTenant(tenant);
        PlanLimits limits = extractPlanLimits(settings);

        return mapToTenantDetailDto(tenant, adminInfo, userCount, settings, limits, authorities);
    }

    @Transactional
    public TenantDetailDto suspendTenant(UUID id) {
        Tenant tenant = findOrThrow(id);

        if (tenant.isSuspended()) {
            throw new IllegalStateException("El tenant ya está suspendido.");
        }

        tenant.setSubscriptionStatus(SubscriptionStatus.SUSPENDED);
        tenantRepository.save(tenant);

        revocationService.revokeAllTokensForTenant(id, Instant.now());

        log.info("Tenant {} ({}) suspendido por superadmin.", tenant.getName(), tenant.getSlug());

        return getTenantDetails(id);
    }

    @Transactional
    public TenantDetailDto reactivateTenant(UUID id) {
        Tenant tenant = findOrThrow(id);

        if (!tenant.isSuspended()) {
            throw new IllegalStateException("El tenant no está suspendido.");
        }

        tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        tenantRepository.save(tenant);

        revocationService.reactivateTenant(id);

        log.info("Tenant {} ({}) reactivado por superadmin.", tenant.getName(), tenant.getSlug());

        return getTenantDetails(id);
    }

    @Transactional
    public void hardDeleteTenant(UUID id, String confirmText) {
        Tenant tenant = findOrThrow(id);

        if (!tenant.getName().equals(confirmText)) {
            throw new IllegalArgumentException("El texto de confirmación no coincide con el nombre del tenant.");
        }

        revocationService.revokeAllTokensForTenant(id, Instant.now());

        // IMPORTANT: Delete entities in correct order to avoid foreign key violations
        // Order: Documents -> Templates -> Patients -> Roles -> Users -> Tenant
        log.info("Hard delete: removing all related entities for tenant {}", tenant.getSlug());

        documentRepository.deleteAllByTenantId(id);
        documentTemplateRepository.deleteAllByTenantId(id);
        patientRepository.deleteAllByTenantId(id);

        // Delete pivot tables before roles/users
        roleRepository.deleteAllRoleUserByTenantId(id);
        roleRepository.deleteAllRolePermissionByTenantId(id);
        roleRepository.deleteAllByTenantId(id);
        userRepository.deleteAllByTenantId(id);

        tenantRepository.delete(tenant);

        log.info("Tenant {} ({}) deleted (hard) by superadmin.", tenant.getName(), tenant.getSlug());
    }

    public TenantDetailDto updateTenantStatus(UUID id, String action) {
        if ("SUSPEND".equalsIgnoreCase(action)) {
            return suspendTenant(id);
        } else if ("REACTIVATE".equalsIgnoreCase(action)) {
            return reactivateTenant(id);
        }
        throw new IllegalArgumentException("Acción no válida. Use SUSPEND o REACTIVATE.");
    }

    // ── SUSCRIPCIÓN: Renovación y Cambio de Plan ─────────────────────────────

    @Transactional
    public RenewSubscriptionResponseDto renewSubscription(String slug, String plan) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + slug));

        SubscriptionPlan newPlan = SubscriptionPlan.valueOf(plan.toUpperCase());
        tenant.setSubscriptionPlan(newPlan);
        tenant.setSubscriptionStartDate(LocalDate.now());
        tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        tenantRepository.save(tenant);

        LocalDate newEndDate = LocalDate.now().plusDays(30);

        log.info("Tenant {} renovó suscripción al plan {}", slug, newPlan);

        return new RenewSubscriptionResponseDto(
                newPlan.name(),
                LocalDate.now(),
                newEndDate,
                "Suscripción renovada exitosamente"
        );
    }

    @Transactional
    public ChangePlanResponseDto changePlan(String slug, String newPlan) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + slug));

        SubscriptionPlan currentPlan = tenant.getSubscriptionPlan();
        SubscriptionPlan targetPlan = SubscriptionPlan.valueOf(newPlan.toUpperCase());

        if (currentPlan == targetPlan) {
            throw new IllegalArgumentException("El plan seleccionado es el mismo que el actual");
        }

        tenant.setSubscriptionPlan(targetPlan);

        Map<String, Object> settings = tenant.getSettings();
        if (settings == null) settings = new HashMap<>();
        Map<String, Object> limits = getPlanLimits(targetPlan);
        settings.put("limits", limits);
        tenant.setSettings(settings);

        tenantRepository.save(tenant);

        LocalDate endDate = tenant.getSubscriptionStartDate().plusDays(30);

        log.info("Tenant {} cambió de plan {} a {}", slug, currentPlan, targetPlan);

        return new ChangePlanResponseDto(
                currentPlan.name(),
                targetPlan.name(),
                endDate,
                "Plan cambiado exitosamente"
        );
    }

    private Map<String, Object> getPlanLimits(SubscriptionPlan plan) {
        return switch (plan) {
            case BASIC -> Map.of(
                    "maxUsers", 10,
                    "maxStorageMB", 1000,
                    "maxApiCallsPerMonth", 1000
            );
            case PRO -> Map.of(
                    "maxUsers", 50,
                    "maxStorageMB", 10000,
                    "maxApiCallsPerMonth", 10000
            );
            case ENTERPRISE -> Map.of(
                    "maxUsers", 999999,
                    "maxStorageMB", 999999999,
                    "maxApiCallsPerMonth", 999999999
            );
        };
    }

    private TenantInfoDto mapToTenantInfoDto(Tenant tenant, AdminInfoDto adminInfo, Set<String> authorities) {
        return new TenantInfoDto(
                authorities.contains("tenant:read:name") ? tenant.getName() : null,
                authorities.contains("tenant:read:slug") ? tenant.getSlug() : null,
                authorities.contains("tenant:read:email") ? tenant.getEmail() : null,
                authorities.contains("tenant:read:phone") ? tenant.getPhone() : null,
                authorities.contains("tenant:read:address") ? tenant.getAddress() : null,
                authorities.contains("tenant:read:subscription_plan") ? tenant.getSubscriptionPlan() : null,
                authorities.contains("tenant:read:subscription_status") ? tenant.getSubscriptionStatus() : null,
                authorities.contains("tenant:read:subscription_start_date") ? tenant.getSubscriptionStartDate() : null,
                authorities.contains("tenant:read:subscription_start_date") ? tenant.getSubscriptionStartDate().plusDays(30) : null,
                adminInfo != null ? adminInfo.firstName() : null,
                adminInfo != null ? adminInfo.lastName() : null,
                adminInfo != null ? adminInfo.email() : null,
                adminInfo != null ? adminInfo.phone() : null,
                authorities.contains("tenant:read:logo_url") ? tenant.getLogoUrl() : null
        );
    }

    private TenantListItemDto mapToTenantListItemDto(Tenant tenant, Set<String> authorities) {
        AdminInfoDto adminInfo = extractAdminInfo(tenant.getSlug());
        int userCount = tenantRepository.countActiveUsersByTenantId(tenant.getId());

        LocalDate endDate = tenant.getSubscriptionStartDate() != null
                ? tenant.getSubscriptionStartDate().plusDays(30)
                : null;

        return new TenantListItemDto(
                authorities.contains("tenant:read:id") ? tenant.getId() : null,
                authorities.contains("tenant:read:name") ? tenant.getName() : null,
                authorities.contains("tenant:read:slug") ? tenant.getSlug() : null,
                authorities.contains("tenant:read:subscription_plan") ? tenant.getSubscriptionPlan() : null,
                authorities.contains("tenant:read:subscription_status") ? tenant.getSubscriptionStatus() : null,
                authorities.contains("tenant:read:subscription_start_date") ? endDate : null,
                adminInfo != null ? adminInfo.firstName() + " " + adminInfo.lastName() : null,
                adminInfo != null ? adminInfo.email() : null,
                userCount,
                authorities.contains("tenant:read:created_at") ? tenant.getCreatedAt() : null,
                authorities.contains("tenant:read:updated_at") ? tenant.getUpdatedAt() : null
        );
    }

    private TenantDetailDto mapToTenantDetailDto(Tenant tenant, AdminInfoDto adminInfo, int userCount, Map<String, Object> settings, PlanLimits limits, Set<String> authorities) {
        TenantStatsDto stats = new TenantStatsDto(
                userCount,
                limits.maxUsers(),
                0L,
                limits.maxStorageMB(),
                0L,
                limits.maxApiCalls()
        );

        return new TenantDetailDto(
                authorities.contains("tenant:read:id") ? tenant.getId() : null,
                authorities.contains("tenant:read:name") ? tenant.getName() : null,
                authorities.contains("tenant:read:slug") ? tenant.getSlug() : null,
                authorities.contains("tenant:read:email") ? tenant.getEmail() : null,
                authorities.contains("tenant:read:phone") ? tenant.getPhone() : null,
                authorities.contains("tenant:read:address") ? tenant.getAddress() : null,
                authorities.contains("tenant:read:subscription_plan") ? tenant.getSubscriptionPlan() : null,
                authorities.contains("tenant:read:subscription_status") ? tenant.getSubscriptionStatus() : null,
                authorities.contains("tenant:read:subscription_start_date") ? tenant.getSubscriptionStartDate() : null,
                authorities.contains("tenant:read:subscription_start_date") ? tenant.getSubscriptionStartDate().plusDays(30) : null,
                authorities.contains("tenant:read:settings") ? tenant.getSettings() : null,
                adminInfo,
                stats,
                authorities.contains("tenant:read:created_at") ? tenant.getCreatedAt() : null,
                authorities.contains("tenant:read:updated_at") ? tenant.getUpdatedAt() : null
        );
    }

    private void validateUpdateBasicInfoPermissions(Map<String, Object> data, Set<String> authorities) {
        if (data.containsKey("name")) requireAuthority(authorities, "tenant:update:name");
        if (data.containsKey("email")) requireAuthority(authorities, "tenant:update:email");
        if (data.containsKey("phone")) requireAuthority(authorities, "tenant:update:phone");
        if (data.containsKey("address")) requireAuthority(authorities, "tenant:update:address");
        if (data.containsKey("logoUrl")) requireAuthority(authorities, "tenant:update:logo_url");
    }


}
