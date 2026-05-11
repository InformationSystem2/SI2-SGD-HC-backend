package com.sgd_hc;

import com.sgd_hc.tenants.entity.SubscriptionPlan;
import com.sgd_hc.tenants.entity.SubscriptionStatus;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.users.entity.DocumentType;
import com.sgd_hc.users.entity.Permission;
import com.sgd_hc.users.entity.Role;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.repository.PermissionRepository;
import com.sgd_hc.users.repository.RoleRepository;
import com.sgd_hc.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.sgd_hc.security.config.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final TenantRepository    tenantRepository;
    private final UserRepository      userRepository;
    private final RoleRepository      roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder     passwordEncoder;

    @Value("${app.seed.system.slug}")     private String systemSlug;
    @Value("${app.seed.system.name}")     private String systemName;
    @Value("${app.seed.system.username}") private String systemUsername;
    @Value("${app.seed.system.password}") private String systemPassword;
    @Value("${app.seed.system.email}")    private String systemEmail;
    @Value("${app.seed.system.firstName}")  private String systemFirstName;
    @Value("${app.seed.system.lastName}")   private String systemLastName;
    @Value("${app.seed.system.nationalId}") private String systemNationalId;

    @Value("${app.seed.default.slug}")     private String defaultSlug;
    @Value("${app.seed.default.name}")     private String defaultName;
    @Value("${app.seed.default.username}") private String defaultUsername;
    @Value("${app.seed.default.password}") private String defaultPassword;
    @Value("${app.seed.default.email}")    private String defaultEmail;
    @Value("${app.seed.default.firstName}")  private String defaultFirstName;
    @Value("${app.seed.default.lastName}")   private String defaultLastName;
    @Value("${app.seed.default.nationalId}") private String defaultNationalId;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info(">>> Iniciando DataInitializer...");
        TenantContext.setBypassFilter(true);

        try {
            Tenant defaultTenant = setupDefaultTenant();
            Tenant hqCoreTenant = tenantRepository.findBySlug(systemSlug)
                    .orElseThrow(() -> new IllegalStateException("Tenant maestro no encontrado: " + systemSlug));

            Set<Permission> allPermissions = createDefaultPermissions();

            Map<String, String> rolesToCreate = Map.of(
                    "ROLE_SUPERUSER", "Superusuario con acceso total",
                    "ROLE_ADMIN",     "Administrador con restricciones de borrado",
                    "ROLE_MEDICO",    "Personal médico del sistema",
                    "ROLE_ARCHIVO",   "Encargado de archivo histórico",
                    "ROLE_DIRECTOR",  "Director del hospital"
            );

            for (Map.Entry<String, String> entry : rolesToCreate.entrySet()) {
                String roleName = entry.getKey();
                String roleDesc = entry.getValue();

                Tenant roleTenant = hqCoreTenant;

                boolean exists = roleRepository.findByNameAndTenantId(roleName, roleTenant.getId()).isPresent();
                if (!exists) {
                    log.info(">>> Creando rol: {} en tenant: {}", roleName, roleTenant.getSlug());
                    try {
                        Role role = roleRepository.saveAndFlush(Role.builder()
                                .name(roleName)
                                .description(roleDesc)
                                .tenant(roleTenant)
                                .build());

                        if (roleName.equals("ROLE_SUPERUSER")) {
                            role.setPermissions(allPermissions);
                            roleRepository.saveAndFlush(role);
                        } else if (roleName.equals("ROLE_ADMIN")) {
                            role.setPermissions(allPermissions);
                            roleRepository.saveAndFlush(role);
                        }
                    } catch (Exception e) {
                        log.warn(">>> Conflicto al crear rol {}: {}", roleName, e.getMessage());
                    }
                }
            }

            Role superuserRole = roleRepository.findByNameAndTenantId("ROLE_SUPERUSER", hqCoreTenant.getId()).orElseThrow();
            Role adminRole     = roleRepository.findByNameAndTenantId("ROLE_ADMIN", hqCoreTenant.getId()).orElseThrow();

            setupUser(systemUsername, systemEmail, systemFirstName, systemLastName, systemPassword, DocumentType.CI, systemNationalId, superuserRole, hqCoreTenant);
            setupUser(defaultUsername, defaultEmail, defaultFirstName, defaultLastName, defaultPassword, DocumentType.CI, defaultNationalId, adminRole,     defaultTenant);

            log.info(">>> DataInitializer fuser_notification_preferencesinalizado correctamente.");
        } finally {
            // HIGIENE DE CÓDIGO: Asegurar siempre la limpieza del ThreadLocal, incluso en la inicialización
            TenantContext.clear();
        }
    }


    private Tenant setupDefaultTenant() {
        // Tenant del sistema para superadmins (acceso global, sin filtro de tenant)
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
                    .build());
        });

        // Tenant por defecto para demo/clínica inicial
        return tenantRepository.findBySlug(defaultSlug).orElseGet(() -> {
            log.info(">>> Creando tenant por defecto '{}'...", defaultSlug);
            return tenantRepository.saveAndFlush(Tenant.builder()
                    .name(defaultName)
                    .slug(defaultSlug)
                    .email(defaultEmail)
                    .phone("+591-123-4567")
                    .address("Calle Principal #123")
                    .subscriptionPlan(SubscriptionPlan.PRO)
                    .subscriptionStatus(SubscriptionStatus.ACTIVE)
                    .subscriptionStartDate(LocalDate.now())
                    .build());
        });
    }

    private void setupUser(String username, String email, String first, String last,
                           String pass, DocumentType docType, String docNum,
                           Role role, Tenant tenant) {
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

    private Set<Permission> createDefaultPermissions() {
        List<String[]> definitions = List.of(
            new String[]{"USER_READ",         "USERS",       "READ"},
            new String[]{"USER_CREATE",       "USERS",       "CREATE"},
            new String[]{"USER_UPDATE",       "USERS",       "UPDATE"},
            new String[]{"USER_DELETE",       "USERS",       "DELETE"},
            new String[]{"ROLE_READ",         "ROLES",       "READ"},
            new String[]{"ROLE_CREATE",       "ROLES",       "CREATE"},
            new String[]{"ROLE_UPDATE",       "ROLES",       "UPDATE"},
            new String[]{"ROLE_DELETE",       "ROLES",       "DELETE"},
            new String[]{"PERMISSION_READ",   "PERMISSIONS", "READ"},
            new String[]{"PERMISSION_CREATE", "PERMISSIONS", "CREATE"},
            new String[]{"PERMISSION_UPDATE", "PERMISSIONS", "UPDATE"},
            new String[]{"PERMISSION_DELETE", "PERMISSIONS", "DELETE"},
            new String[]{"PATIENT_READ",      "PATIENTS",    "READ"},
            new String[]{"PATIENT_CREATE",    "PATIENTS",    "CREATE"},
            new String[]{"PATIENT_UPDATE",    "PATIENTS",    "UPDATE"},
            new String[]{"PATIENT_DELETE",    "PATIENTS",    "DELETE"},
            new String[]{"DOCUMENT_READ",     "DOCUMENTS",   "READ"},
            new String[]{"DOCUMENT_CREATE",   "DOCUMENTS",   "CREATE"},
            new String[]{"DOCUMENT_UPDATE",   "DOCUMENTS",   "UPDATE"},
            new String[]{"DOCUMENT_DELETE",   "DOCUMENTS",   "DELETE"},
            new String[]{"TEMPLATE_READ",     "TEMPLATES",   "READ"},
            new String[]{"TEMPLATE_CREATE",   "TEMPLATES",   "CREATE"},
            new String[]{"TEMPLATE_UPDATE",   "TEMPLATES",   "UPDATE"},
            new String[]{"TEMPLATE_DELETE",   "TEMPLATES",   "DELETE"}
        );

        Set<String> existingNames = new HashSet<>();
        permissionRepository.findAll().forEach(p -> existingNames.add(p.getName().toUpperCase().trim()));

        Set<Permission> permissions = new HashSet<>();
        for (String[] def : definitions) {
            String name   = def[0];
            String module = def[1];
            String action = def[2];

            Permission p;
            if (existingNames.contains(name.toUpperCase())) {
                p = permissionRepository.findByName(name).orElse(null);
            } else {
                try {
                    log.info(">>> Creando permiso: {}", name);
                    p = permissionRepository.saveAndFlush(Permission.builder()
                            .name(name)
                            .module(module)
                            .action(action)
                            .description("Permiso para " + name)
                            .build());
                } catch (Exception e) {
                    log.warn(">>> Conflicto al crear permiso {}: {}", name, e.getMessage());
                    p = permissionRepository.findByName(name).orElse(null);
                }
            }
            if (p != null) permissions.add(p);
        }
        return permissions;
    }
}
