package com.sgd_hc.tenants.service;

import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.service.AuditableService;

import com.sgd_hc.tenants.dto.*;
import com.sgd_hc.tenants.entity.*;
import com.sgd_hc.tenants.mapper.TenantMapper;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.tenants.utils.TagSlugGenerator;
import com.sgd_hc.users.entity.Role;
import com.sgd_hc.users.entity.User;

import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.exception.StripeException;

import com.sgd_hc.users.repository.RoleRepository;
import com.sgd_hc.users.repository.UserRepository;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.documents.repository.DocumentTemplateRepository;
import com.sgd_hc.patients.repository.PatientRepository;
import com.sgd_hc.dicom.repository.DicomStudyRepository;
import com.sgd_hc.dicom.repository.DicomInstanceRepository;

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
import org.springframework.data.redis.core.RedisTemplate;
import com.sgd_hc.config.mail.EmailService;

import java.io.IOException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService implements AuditableService<Object, Tenant> {

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentTemplateRepository documentTemplateRepository;
    private final PatientRepository patientRepository;
    private final DicomStudyRepository dicomStudyRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final TenantSessionService sessionService;
    private final TenantRevocationService revocationService;
    private final PlanService planService;
    private final ObjectMapper objectMapper;
    private final TenantMapper tenantMapper;

    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    private final EmailService emailService;

    @Value("${app.seed.system.slug}")
    private String systemSlug;

    @Value("${spring.profiles.active}")
    private String activeProfile;

    private static final int MONTHLY_DAYS = 30;
    private static final int YEARLY_DAYS = 365;

    // ── PÚBLICO (Registro y Pago) ─────────────────────────────────

    @Transactional
    public Map<String, Object> startRegistration(TenantRegisterRequestDto dto) {
        var sessionOpt = sessionService.getSession(dto.sessionToken());
        if (sessionOpt.isEmpty()) {
            throw new IllegalArgumentException("Sesión de registro expirada o inválida. Por favor, inicie nuevamente.");
        }

        String redisKey = "verification_code:" + dto.adminEmail();
        String savedCode = redisTemplate.opsForValue().get(redisKey);

        if (savedCode == null || !savedCode.equals(dto.validationCode())) {
            throw new IllegalArgumentException("El código de verificación es incorrecto o ha expirado.");
        }

        var sessionData = sessionOpt.get();

        TenantRegistrationDataDto regData = tenantMapper.toRegistrationData(
                dto,
                passwordEncoder.encode(dto.adminPassword())
        );

        sessionService.saveRegistrationData(dto.sessionToken(), regData);

        redisTemplate.delete(redisKey);

        return Map.of(
                "status", "REGISTRATION_SAVED",
                "message", "Datos guardados. Proceda al pago."
        );
    }

    @Transactional
    public TenantSessionResponseDto initSession(TenantInitSessionDto dto) {
        String billingCycle = dto.billingCycle() != null ? dto.billingCycle() : "MONTHLY";
        String token = sessionService.createSession(dto.selectedPlan(), billingCycle);

        return new TenantSessionResponseDto(
                token,
                "Sesión iniciada. Proceda a registrar los datos de su clínica."
        );
    }

    public SendCodeResponseDto sendVerificationCode(SendCodeRequestDto dto) {
        String email = dto.getEmail();
        String code = String.format("%06d", new Random().nextInt(999999));
        String redisKey = "verification_code:" + email;

        redisTemplate.opsForValue().set(redisKey, code, Duration.ofMinutes(10));

        if ("dev".equalsIgnoreCase(activeProfile)) {
            log.info("Entorno dev: Código generado para {}: {}", email, code);
            return new SendCodeResponseDto("Código generado para pruebas", code);
        } else {
            boolean sent = emailService.sendVerificationCode(email, code);
            if (!sent) {
                throw new IllegalStateException("Error al enviar el correo de verificación. Intente nuevamente.");
            }
            return new SendCodeResponseDto("Código enviado exitosamente", null);
        }
    }

    @Transactional
    @Auditable(resourceType = "TENANT", actionType = ActionType.CREATE, idParamName = "slug")
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

        String billingCycle = sessionData.getBillingCycle() != null ? sessionData.getBillingCycle() : "MONTHLY";

        TenantContext.setBypassFilter(true);

        try {
            Tenant tenant = createTenantWithAdmin(regData, sessionData.getPlan(), billingCycle);
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

    private int getCycleDays(String billingCycle) {
        return "YEARLY".equalsIgnoreCase(billingCycle) ? YEARLY_DAYS : MONTHLY_DAYS;
    }

    private Tenant createTenantWithAdmin(TenantRegistrationDataDto regData, String planName, String billingCycle) {
        String finalSlug = generateUniqueSlug(regData.getTenantName());

        SubscriptionPlan plan = SubscriptionPlan.valueOf(planName.toUpperCase());
        Tenant tenant = tenantMapper.toEntity(regData, finalSlug, plan);
        tenant.setBillingCycle(billingCycle != null ? billingCycle.toUpperCase() : "MONTHLY");
        tenant.setSubscriptionEndDate(LocalDate.now().plusDays(getCycleDays(tenant.getBillingCycle())));
        tenant.setSettings(buildDefaultSettings(planName));
        tenant = tenantRepository.save(tenant);

        Tenant systemTenant = tenantRepository.findBySlug(systemSlug)
                .orElseThrow(() -> new IllegalStateException("Tenant maestro no encontrado."));

        Role adminRole = roleRepository.findByNameAndTenantId("ROLE_ADMIN", systemTenant.getId())
                .orElseThrow(() -> new IllegalStateException("Rol global ADMIN no encontrado."));

        User admin = tenantMapper.toAdminUserEntity(regData, adminRole, tenant);
        userRepository.save(admin);

        return tenant;
    }

    private Map<String, Object> buildDefaultSettings(String planName) {
        Map<String, Object> settings = new HashMap<>();
        settings.putAll(TenantSettingsDefaults.getAllDefaults());

        Map<String, Object> limits = new HashMap<>();
        planService.getLimitsForPlan(planName).forEach(limits::put);
        settings.put("limits", limits);

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
    @Auditable(resourceType = "TENANT", actionType = ActionType.UPDATE, idParamName = "slug")
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
    @Auditable(resourceType = "TENANT_SETTINGS", actionType = ActionType.UPDATE, idParamName = "slug")
    public Map<String, Object> updateSettingsBySlug(String slug, Map<String, Object> newSettings) {
        requireAuthority(currentAuthorities(), "tenant:update:settings");
        Tenant tenant = findTenantBySlugOrThrow(slug);
        return updateSettingsOnTenant(tenant, newSettings);
    }

    public TenantStatsDto getTenantStats(String slug) {
        Tenant tenant = findTenantBySlugOrThrow(slug);

        String planName = tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan().name() : "BASIC";
        Map<String, Long> limits = planService.getLimitsForPlan(planName);

        int userCount = userRepository.findAllByTenantId(tenant.getId()).size();
        long storageUsedBytes = documentRepository.sumFileSizeBytesByTenantId(tenant.getId())
                + dicomInstanceRepository.sumFileSizeBytesByTenantId(tenant.getId());
        long storageUsedMB = storageUsedBytes / (1024 * 1024);
        long patientCount = patientRepository.countByTenantId(tenant.getId());
        long documentCount = documentRepository.countByTenantId(tenant.getId());
        long dicomStudyCount = dicomStudyRepository.countByTenantId(tenant.getId());
        long roleCount = roleRepository.countByTenantId(tenant.getId());

        return new TenantStatsDto(
                userCount,
                limits.getOrDefault("maxUsers", 0L).intValue(),
                storageUsedMB,
                limits.getOrDefault("maxStorageMB", 0L),
                0L,
                limits.getOrDefault("maxApiCallsPerMonth", 0L),
                patientCount,
                limits.getOrDefault("maxPatients", 0L),
                documentCount,
                limits.getOrDefault("maxDocuments", 0L),
                dicomStudyCount,
                limits.getOrDefault("maxDicomStudies", 0L),
                roleCount,
                limits.getOrDefault("maxStaffRoles", 0L)
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

    private Map<String, Object> getSettingsFromTenant(Tenant tenant) {
        Map<String, Object> settings = tenant.getSettings();
        if (settings == null || settings.isEmpty()) {
            // Read limits from PlanService as single source of truth
            String planName = tenant.getSubscriptionPlan() != null
                    ? tenant.getSubscriptionPlan().name()
                    : "BASIC";
            Map<String, Object> defaults = TenantSettingsDefaults.getAllDefaults();
            Map<String, Long> planLimits = planService.getLimitsForPlan(planName);
            Map<String, Object> planLimitsObj = new HashMap<>(planLimits);
            defaults.put("limits", planLimitsObj);
            return defaults;
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
        String planName = tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan().name() : "BASIC";
        Map<String, Long> planLimits = planService.getLimitsForPlan(planName);

        return mapToTenantDetailDto(tenant, adminInfo, userCount, settings, planLimits, authorities);
    }

    @Transactional
    @Auditable(resourceType = "TENANT", actionType = ActionType.UPDATE, idParamName = "id")
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
    @Auditable(resourceType = "TENANT", actionType = ActionType.UPDATE, idParamName = "id")
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
    @Auditable(resourceType = "TENANT", actionType = ActionType.DELETE, idParamName = "id")
    public void hardDeleteTenant(UUID id, String confirmText) {
        Tenant tenant = findOrThrow(id);

        if (!tenant.getName().equals(confirmText)) {
            throw new IllegalArgumentException("El texto de confirmación no coincide con el nombre del tenant.");
        }

        revocationService.revokeAllTokensForTenant(id, Instant.now());

        log.info("Hard delete: removing all related entities for tenant {}", tenant.getSlug());

        documentRepository.deleteAllByTenantId(id);
        documentTemplateRepository.deleteAllByTenantId(id);
        patientRepository.deleteAllByTenantId(id);

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
    @Auditable(resourceType = "TENANT_SUBSCRIPTION", actionType = ActionType.UPDATE, idParamName = "slug")
    public RenewSubscriptionResponseDto renewSubscription(String slug, String plan, String billingCycle) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + slug));

        SubscriptionPlan subPlan = SubscriptionPlan.valueOf(dto.plan().toUpperCase());
        long expectedAmount = getPlanPriceInCents(dto.plan());

        if (expectedAmount > 0) {
            if (dto.paymentIntentId() == null || dto.paymentIntentId().isBlank()) {
                throw new IllegalArgumentException("El ID de pago es requerido para renovar un plan de pago.");
            }
            try {
                PaymentIntent intent = PaymentIntent.retrieve(dto.paymentIntentId());
                if (!"succeeded".equalsIgnoreCase(intent.getStatus())) {
                    throw new IllegalStateException("El pago no ha sido completado. Estado: " + intent.getStatus());
                }
                if (!"bob".equalsIgnoreCase(intent.getCurrency())) {
                    throw new IllegalArgumentException("Moneda inválida. Se requiere BOB.");
                }
                if (intent.getAmount() < expectedAmount) {
                    throw new IllegalArgumentException("Monto de pago insuficiente.");
                }
                if (!slug.equals(intent.getMetadata().get("tenantSlug"))) {
                    throw new IllegalArgumentException("El pago no pertenece a esta clínica.");
                }
            } catch (StripeException e) {
                throw new RuntimeException("Error al verificar pago con Stripe: " + e.getMessage(), e);
            }
        }

        tenant.setSubscriptionPlan(subPlan);
        tenant.setSubscriptionStartDate(LocalDate.now());

        String effectiveBillingCycle = billingCycle != null ? billingCycle.toUpperCase() : tenant.getBillingCycle();
        if (billingCycle != null) {
            tenant.setBillingCycle(effectiveBillingCycle);
        }

        tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);

        Map<String, Object> settings = tenant.getSettings();
        if (settings == null) settings = new HashMap<>();

        Map<String, Object> limits = new HashMap<>();
        planService.getLimitsForPlan(plan).forEach(limits::put);
        settings.put("limits", limits);
        tenant.setSettings(settings);

        int cycleDays = getCycleDays(effectiveBillingCycle);
        LocalDate newEndDate = LocalDate.now().plusDays(cycleDays);
        tenant.setSubscriptionEndDate(newEndDate);

        tenantRepository.save(tenant);

        log.info("Tenant {} renovó suscripción al plan {} (ciclo {})", slug, newPlan, effectiveBillingCycle);

        return new RenewSubscriptionResponseDto(
                newPlan.name(),
                effectiveBillingCycle,
                LocalDate.now(),
                newEndDate,
                "Suscripción renovada exitosamente"
        );
    }

    @Transactional
    @Auditable(resourceType = "TENANT_SUBSCRIPTION", actionType = ActionType.UPDATE, idParamName = "slug")
    public ChangePlanResponseDto changePlan(String slug, String newPlanName) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + slug));

        SubscriptionPlan currentPlan = tenant.getSubscriptionPlan();
        SubscriptionPlan targetPlan = SubscriptionPlan.valueOf(newPlanName.toUpperCase());

        if (currentPlan == targetPlan) {
            throw new IllegalArgumentException("El plan seleccionado es el mismo que el actual");
        }

        long expectedAmount = getPlanPriceInCents(dto.newPlan());

        if (expectedAmount > 0) {
            if (dto.paymentIntentId() == null || dto.paymentIntentId().isBlank()) {
                throw new IllegalArgumentException("El ID de pago es requerido para cambiar a un plan de pago.");
            }
            try {
                PaymentIntent intent = PaymentIntent.retrieve(dto.paymentIntentId());
                if (!"succeeded".equalsIgnoreCase(intent.getStatus())) {
                    throw new IllegalStateException("El pago no ha sido completado. Estado: " + intent.getStatus());
                }
                if (!"bob".equalsIgnoreCase(intent.getCurrency())) {
                    throw new IllegalArgumentException("Moneda inválida. Se requiere BOB.");
                }
                if (intent.getAmount() < expectedAmount) {
                    throw new IllegalArgumentException("Monto de pago insuficiente.");
                }
                if (!slug.equals(intent.getMetadata().get("tenantSlug"))) {
                    throw new IllegalArgumentException("El pago no pertenece a esta clínica.");
                }
                if (!dto.newPlan().equalsIgnoreCase(intent.getMetadata().get("plan"))) {
                    throw new IllegalArgumentException("El plan pagado no coincide con el seleccionado.");
                }
            } catch (StripeException e) {
                throw new RuntimeException("Error al verificar pago con Stripe: " + e.getMessage(), e);
            }
        }

        tenant.setSubscriptionPlan(targetPlan);

        Map<String, Object> settings = tenant.getSettings();
        if (settings == null) settings = new HashMap<>();

        Map<String, Object> limits = new HashMap<>();
        planService.getLimitsForPlan(newPlanName).forEach(limits::put);
        settings.put("limits", limits);
        tenant.setSettings(settings);

        int cycleDays = getCycleDays(tenant.getBillingCycle());
        LocalDate endDate = tenant.getSubscriptionStartDate().plusDays(cycleDays);
        tenant.setSubscriptionEndDate(endDate);

        tenantRepository.save(tenant);

        log.info("Tenant {} cambió de plan {} a {}", slug, currentPlan, targetPlan);

        return new ChangePlanResponseDto(
                currentPlan.name(),
                targetPlan.name(),
                tenant.getBillingCycle(),
                endDate,
                "Plan cambiado exitosamente"
        );
    }

    // ── MAPPERS ──────────────────────────────────────────────────────────────

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
                authorities.contains("tenant:read:subscription_start_date") ? tenant.getSubscriptionEndDate() : null,
                authorities.contains("tenant:read:subscription_plan") ? tenant.getBillingCycle() : null,
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

        return new TenantListItemDto(
                authorities.contains("tenant:read:id") ? tenant.getId() : null,
                authorities.contains("tenant:read:name") ? tenant.getName() : null,
                authorities.contains("tenant:read:slug") ? tenant.getSlug() : null,
                authorities.contains("tenant:read:subscription_plan") ? tenant.getSubscriptionPlan() : null,
                authorities.contains("tenant:read:subscription_status") ? tenant.getSubscriptionStatus() : null,
                authorities.contains("tenant:read:subscription_start_date") ? tenant.getSubscriptionEndDate() : null,
                authorities.contains("tenant:read:subscription_plan") ? tenant.getBillingCycle() : null,
                adminInfo != null ? adminInfo.firstName() + " " + adminInfo.lastName() : null,
                adminInfo != null ? adminInfo.email() : null,
                userCount,
                authorities.contains("tenant:read:created_at") ? tenant.getCreatedAt() : null,
                authorities.contains("tenant:read:updated_at") ? tenant.getUpdatedAt() : null
        );
    }

    private TenantDetailDto mapToTenantDetailDto(Tenant tenant, AdminInfoDto adminInfo, int userCount,
                                                  Map<String, Object> settings, Map<String, Long> planLimits,
                                                  Set<String> authorities) {
        long storageUsedBytes = documentRepository.sumFileSizeBytesByTenantId(tenant.getId())
                + dicomInstanceRepository.sumFileSizeBytesByTenantId(tenant.getId());
        long storageUsedMB = storageUsedBytes / (1024 * 1024);

        TenantStatsDto stats = new TenantStatsDto(
                userCount,
                planLimits.getOrDefault("maxUsers", 0L).intValue(),
                storageUsedMB,
                planLimits.getOrDefault("maxStorageMB", 0L),
                0L,
                planLimits.getOrDefault("maxApiCallsPerMonth", 0L),
                patientRepository.countByTenantId(tenant.getId()),
                planLimits.getOrDefault("maxPatients", 0L),
                documentRepository.countByTenantId(tenant.getId()),
                planLimits.getOrDefault("maxDocuments", 0L),
                dicomStudyRepository.countByTenantId(tenant.getId()),
                planLimits.getOrDefault("maxDicomStudies", 0L),
                roleRepository.countByTenantId(tenant.getId()),
                planLimits.getOrDefault("maxStaffRoles", 0L)
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
                authorities.contains("tenant:read:subscription_start_date") ? tenant.getSubscriptionEndDate() : null,
                authorities.contains("tenant:read:subscription_plan") ? tenant.getBillingCycle() : null,
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

    @Override
    public Tenant getEntity(Object id) {
        if (id instanceof UUID) return findOrThrow((UUID) id);
        if (id instanceof String) return findTenantBySlugOrThrow((String) id);
        return null;
    }

    @Override
    public Map<String, Object> toAuditMap(Tenant entity) {
        return tenantMapper.toAuditMap(entity);
    }
}
