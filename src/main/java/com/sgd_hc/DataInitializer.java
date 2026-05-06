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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info(">>> Iniciando DataInitializer...");

        Tenant defaultTenant = setupDefaultTenant();

        Set<Permission> allPermissions = createDefaultPermissions();
        Set<Permission> limitedPermissions = new HashSet<>(allPermissions);
        limitedPermissions.removeIf(p -> p.getName().endsWith("_DELETE"));

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

            Role role = roleRepository.findByName(roleName).orElse(null);

            if (role == null) {
                log.info(">>> Creando rol: {}", roleName);
                try {
                    role = roleRepository.saveAndFlush(Role.builder()
                            .name(roleName)
                            .description(roleDesc)
                            .tenant(defaultTenant)
                            .build());
                } catch (Exception e) {
                    log.warn(">>> Conflicto al crear rol {}: {}", roleName, e.getMessage());
                    role = roleRepository.findByName(roleName).orElseThrow();
                }
            }

            if (roleName.equals("ROLE_SUPERUSER")) {
                role.setPermissions(allPermissions);
                roleRepository.saveAndFlush(role);
            } else if (roleName.equals("ROLE_ADMIN")) {
                role.setPermissions(limitedPermissions);
                roleRepository.saveAndFlush(role);
            }
        }

        Role superuserRole = roleRepository.findByName("ROLE_SUPERUSER").orElseThrow();
        Role adminRole     = roleRepository.findByName("ROLE_ADMIN").orElseThrow();

        setupUser("superuser", "superuser@sgd.com", "Super",  "User",    "superuser123", DocumentType.CI, "0000000", superuserRole, defaultTenant);
        setupUser("admin",     "admin@sgd.com",     "Admin",  "Limited", "admin123",     DocumentType.CI, "1111111", adminRole,     defaultTenant);

        log.info(">>> DataInitializer finalizado correctamente.");
    }

    private Tenant setupDefaultTenant() {
        return tenantRepository.findBySlug("default").orElseGet(() -> {
            log.info(">>> Creando tenant por defecto...");
            return tenantRepository.saveAndFlush(Tenant.builder()
                    .name("Hospital Central")
                    .slug("default")
                    .email("admin@hospital.com")
                    .phone("+591-000-0000")
                    .address("Dirección por defecto")
                    .subscriptionPlan(SubscriptionPlan.ENTERPRISE)
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
            new String[]{"PATIENT_DELETE",    "PATIENTS",    "DELETE"}
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
