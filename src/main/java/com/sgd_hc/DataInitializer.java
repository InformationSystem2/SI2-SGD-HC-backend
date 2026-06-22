package com.sgd_hc;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgd_hc.patients.entity.Gender;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.patients.repository.PatientRepository;
import com.sgd_hc.security.config.tenant.TenantContext;
import com.sgd_hc.tenants.config.TenantSettingsDefaults;
import com.sgd_hc.tenants.entity.SubscriptionPlan;
import com.sgd_hc.tenants.entity.SubscriptionStatus;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.tenants.service.PlanService;
import com.sgd_hc.users.entity.DocumentType;
import com.sgd_hc.users.entity.Permission;
import com.sgd_hc.users.entity.Role;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.repository.PermissionRepository;
import com.sgd_hc.users.repository.RoleRepository;
import com.sgd_hc.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final PatientRepository patientRepository;
    private final PlanService planService;

    @Value("${app.seed.system.slug}")
    private String systemSlug;
    @Value("${app.seed.system.name}")
    private String systemName;
    @Value("${app.seed.system.username}")
    private String systemUsername;
    @Value("${app.seed.system.password}")
    private String systemPassword;
    @Value("${app.seed.system.email}")
    private String systemEmail;
    @Value("${app.seed.system.firstName}")
    private String systemFirstName;
    @Value("${app.seed.system.lastName}")
    private String systemLastName;
    @Value("${app.seed.system.nationalId}")
    private String systemNationalId;

    @Value("${app.seed.default.slug}")
    private String defaultSlug;
    @Value("${app.seed.default.name}")
    private String defaultName;
    @Value("${app.seed.default.username}")
    private String defaultUsername;
    @Value("${app.seed.default.password}")
    private String defaultPassword;
    @Value("${app.seed.default.email}")
    private String defaultEmail;
    @Value("${app.seed.default.firstName}")
    private String defaultFirstName;
    @Value("${app.seed.default.lastName}")
    private String defaultLastName;
    @Value("${app.seed.default.nationalId}")
    private String defaultNationalId;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info(">>> Iniciando DataInitializer...");
        TenantContext.setBypassFilter(true);

        try {
            Tenant defaultTenant = setupDefaultTenant();
            Tenant secondTenant = setupSecondTenant();
            Tenant hqCoreTenant = tenantRepository.findBySlug(systemSlug)
                    .orElseThrow(() -> new IllegalStateException("Tenant maestro no encontrado: " + systemSlug));

            Set<Permission> allPermissions = new HashSet<>(permissionRepository.findAll());

            // 1. Crear roles para el tenant del sistema (hqCoreTenant)
            Map<String, String> systemRoles = Map.of(
                    "ROLE_SUPERUSER", "Superusuario con acceso total",
                    "ROLE_ADMIN", "Administrador con restricciones de borrado",
                    "ROLE_MEDICO", "Personal médico del sistema",
                    "ROLE_ARCHIVO", "Encargado de archivo histórico",
                    "ROLE_DIRECTOR", "Director del hospital"
            );

            for (Map.Entry<String, String> entry : systemRoles.entrySet()) {
                String roleName = entry.getKey();
                String roleDesc = entry.getValue();

                boolean exists = roleRepository.findByNameAndTenantId(roleName, hqCoreTenant.getId()).isPresent();
                Role role;
                if (!exists) {
                    log.info(">>> Creando rol del sistema: {} en tenant: {}", roleName, hqCoreTenant.getSlug());
                    role = Role.builder()
                            .name(roleName)
                            .description(roleDesc)
                            .tenant(hqCoreTenant)
                            .permissions(roleName.equals("ROLE_SUPERUSER") || roleName.equals("ROLE_ADMIN") ? allPermissions : new HashSet<>())
                            .build();
                    roleRepository.saveAndFlush(role);
                } else {
                    role = roleRepository.findByNameAndTenantId(roleName, hqCoreTenant.getId()).orElse(null);
                    if (role != null && (roleName.equals("ROLE_SUPERUSER") || roleName.equals("ROLE_ADMIN"))) {
                        role.setPermissions(allPermissions);
                        roleRepository.saveAndFlush(role);
                    }
                }
            }

            // 2. Crear roles específicos para los demás tenants principales
            seedRolesForTenant(defaultTenant, allPermissions);
            seedRolesForTenant(secondTenant, allPermissions);

            setupUser(systemUsername, systemEmail, systemFirstName, systemLastName, systemPassword, DocumentType.CI, systemNationalId, "ROLE_SUPERUSER", hqCoreTenant);
            setupUser(defaultUsername, defaultEmail, defaultFirstName, defaultLastName, defaultPassword, DocumentType.CI, defaultNationalId, "ROLE_ADMIN", defaultTenant);
            setupUser("admin.sur", "admin@clinicasur.com", "Admin", "Sur", "admin123", DocumentType.CI, "2222222", "ROLE_ADMIN", secondTenant);

            seedPatients(defaultTenant);
            seedPatients(secondTenant);
            setupExtraTenants(allPermissions);

            log.info(">>> DataInitializer finalizado correctamente.");
        } finally {
            TenantContext.clear();
        }
    }


    private Tenant setupDefaultTenant() {
        tenantRepository.findBySlug(systemSlug).orElseGet(() -> {
            log.info(">>> Creando tenant de sistema '{}'...", systemSlug);
            return tenantRepository.saveAndFlush(Tenant.builder()
                    .name(systemName)
                    .slug(systemSlug)
                    .email(systemEmail)
                    .phone("+591-000-0000")
                    .address("Sistema central")
                    .subscriptionPlan(SubscriptionPlan.ENTERPRISE)
                    .subscriptionStatus(SubscriptionStatus.ACTIVE)
                    .subscriptionStartDate(LocalDate.now())
                    .subscriptionEndDate(LocalDate.now().plusYears(100))
                    .billingCycle("YEARLY")
                    .build());
        });

        return tenantRepository.findBySlug(defaultSlug).orElseGet(() -> {
            log.info(">>> Creando tenant por defecto '{}'...", defaultSlug);
            Map<String, Object> settings = new HashMap<>();
            settings.putAll(TenantSettingsDefaults.getAllDefaults());
            try {
                ClassPathResource res = new ClassPathResource("default-branding.json");
                Map<String, Object> defaultBranding = objectMapper.readValue(res.getInputStream(), Map.class);
                settings.put("branding", defaultBranding);
            } catch (IOException e) {
                log.warn("No se pudo cargar default-branding.json en DataInitializer");
            }

            return tenantRepository.saveAndFlush(Tenant.builder()
                    .name(defaultName)
                    .slug(defaultSlug)
                    .email(defaultEmail)
                    .phone("+591-123-4567")
                    .address("Calle Principal #123")
                    .subscriptionPlan(SubscriptionPlan.PRO)
                    .subscriptionStatus(SubscriptionStatus.ACTIVE)
                    .subscriptionStartDate(LocalDate.now())
                    .subscriptionEndDate(LocalDate.now().plusDays(30))
                    .billingCycle("MONTHLY")
                    .settings(settings)
                    .build());
        });
    }

    private Tenant setupSecondTenant() {
        return tenantRepository.findBySlug("clinica-sur").orElseGet(() -> {
            log.info(">>> Creando segundo tenant 'clinica-sur'...");
            return tenantRepository.saveAndFlush(Tenant.builder()
                    .name("Clínica del Sur (Demo)")
                    .slug("clinica-sur")
                    .email("admin@clinicasur.com")
                    .phone("+591-765-4321")
                    .address("Avenida Radial 26 #456")
                    .subscriptionPlan(SubscriptionPlan.PRO)
                    .subscriptionStatus(SubscriptionStatus.ACTIVE)
                    .subscriptionStartDate(LocalDate.now())
                    .subscriptionEndDate(LocalDate.now().plusDays(30))
                    .billingCycle("MONTHLY")
                    .build());
        });
    }

    private void seedRolesForTenant(Tenant tenant, Set<Permission> allPermissions) {
        Map<String, String> rolesToCreate = Map.of(
                "ROLE_ADMIN", "Administrador de la clínica",
                "ROLE_MEDICO", "Personal médico del sistema",
                "ROLE_ARCHIVO", "Encargado de archivo histórico",
                "ROLE_DIRECTOR", "Director del hospital"
        );

        for (Map.Entry<String, String> entry : rolesToCreate.entrySet()) {
            String roleName = entry.getKey();
            String roleDesc = entry.getValue();

            boolean exists = roleRepository.findByNameAndTenantId(roleName, tenant.getId()).isPresent();
            if (!exists) {
                log.info(">>> Creando rol: {} en tenant: {}", roleName, tenant.getSlug());
                Role role = Role.builder()
                        .name(roleName)
                        .description(roleDesc)
                        .tenant(tenant)
                        .permissions(roleName.equals("ROLE_ADMIN") ? allPermissions : new HashSet<>())
                        .build();
                roleRepository.saveAndFlush(role);
            }
        }
    }

    private void setupUser(String username, String email, String first, String last,
                           String pass, DocumentType docType, String docNum,
                           String roleName, Tenant tenant) {
        Role role = roleRepository.findByNameAndTenantId(roleName, tenant.getId())
                .orElseThrow(() -> new IllegalStateException("Rol " + roleName + " no encontrado para tenant " + tenant.getSlug()));

        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            log.info(">>> Actualizando usuario: {}", username);
            user.setRoles(new HashSet<>(Set.of(role)));
            user.setPassword(passwordEncoder.encode(pass));
            if (user.getDocumentNumber() == null) {
                user.setDocumentType(docType);
                user.setDocumentNumber(docNum);
            }
            userRepository.saveAndFlush(user);
        } else {
            log.info(">>> Creando usuario: {}", username);
            userRepository.saveAndFlush(User.builder()
                    .username(username)
                    .email(email)
                    .firstName(first)
                    .lastName(last)
                    .password(passwordEncoder.encode(pass))
                    .documentType(docType)
                    .documentNumber(docNum)
                    .gender("M")
                    .isActive(true)
                    .roles(new HashSet<>(Set.of(role)))
                    .tenant(tenant)
                    .build());
        }
    }

    private void seedPatients(Tenant tenant) {
        long existing = patientRepository.countByTenant(tenant);
        if (existing >= 50) {
            log.info(">>> Ya existen {} pacientes para el tenant '{}', se omite el seed.", existing, tenant.getSlug());
            return;
        }

        Faker faker = new Faker(new Locale("es"));
        Gender[] genders = Gender.values();
        DocumentType[] docTypes = {DocumentType.CI, DocumentType.PASAPORTE};
        int toCreate = (int) (50 - existing);

        log.info(">>> Creando {} pacientes de prueba para tenant '{}' con Datafaker...", toCreate, tenant.getSlug());
        for (int i = 0; i < toCreate; i++) {
            Gender gender = genders[faker.random().nextInt(genders.length)];
            String firstName = faker.name().firstName();

            String docNumber;
            do {
                docNumber = String.valueOf(faker.number().numberBetween(1000000L, 9999999L));
            } while (patientRepository.findByDocumentNumber(docNumber).isPresent());

            Patient patient = Patient.builder()
                    .firstName(firstName)
                    .lastName(faker.name().lastName())
                    .documentType(docTypes[faker.random().nextInt(docTypes.length)])
                    .documentNumber(docNumber)
                    .gender(gender)
                    .birthDate(faker.timeAndDate().birthday(1, 90))
                    .phone(faker.phoneNumber().cellPhone())
                    .address(faker.address().fullAddress())
                    .tenant(tenant)
                    .build();

            patientRepository.save(patient);
        }
        log.info(">>> Seed de pacientes completado para tenant '{}'.", tenant.getSlug());
    }

    private void setupExtraTenants(Set<Permission> allPermissions) {
        log.info(">>> Creando 13 clínicas extra para los gráficos del Dashboard...");
        Faker faker = new Faker(new Locale("es"));
        SubscriptionPlan[] planes = SubscriptionPlan.values();
        SubscriptionStatus[] estados = {SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE, SubscriptionStatus.PENDING_PAYMENT};

        for (int i = 1; i <= 13; i++) {
            String slug = "clinica-extra-" + i;
            Tenant extraTenant = tenantRepository.findBySlug(slug).orElseGet(() -> {
                SubscriptionPlan plan = planes[faker.random().nextInt(planes.length)];
                return tenantRepository.saveAndFlush(Tenant.builder()
                        .name(faker.company().name())
                        .slug(slug)
                        .email("admin@" + slug + ".com")
                        .phone(faker.phoneNumber().cellPhone())
                        .address(faker.address().fullAddress())
                        .subscriptionPlan(plan)
                        .subscriptionStatus(estados[faker.random().nextInt(estados.length)])
                        .subscriptionStartDate(LocalDate.now().minusDays(faker.random().nextInt(1, 100)))
                        .subscriptionEndDate(LocalDate.now().plusDays(faker.random().nextInt(5, 60)))
                        .billingCycle("MONTHLY")
                        .build());
            });

            // Crear roles para esta clínica extra
            seedRolesForTenant(extraTenant, allPermissions);

            setupUser("admin." + slug, "admin@" + slug + ".com", "Admin", "Clínica " + i,
                    "admin123", DocumentType.CI, String.valueOf(faker.number().randomNumber(7, true)),
                    "ROLE_ADMIN", extraTenant);

            seedPatients(extraTenant);
        }
    }
}
