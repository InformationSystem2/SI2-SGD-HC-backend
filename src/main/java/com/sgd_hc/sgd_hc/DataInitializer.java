package com.sgd_hc.sgd_hc;

import com.sgd_hc.sgd_hc.module_users.entity.Permission;
import com.sgd_hc.sgd_hc.module_users.entity.Role;
import com.sgd_hc.sgd_hc.module_users.entity.User;
import com.sgd_hc.sgd_hc.module_users.repository.PermissionRepository;
import com.sgd_hc.sgd_hc.module_users.repository.RoleRepository;
import com.sgd_hc.sgd_hc.module_users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info(">>> Iniciando DataInitializer...");

        // 1. Crear Permisos por defecto
        Set<Permission> allPermissions = createDefaultPermissions();
        Set<Permission> limitedPermissions = new HashSet<>(allPermissions);
        limitedPermissions.removeIf(p -> p.getName().endsWith("_DELETE"));

        // 2. Manejo de Roles (Resiliente a PostgreSQL)
        Role superuserRole = roleRepository.findByName("ROLE_SUPERUSER")
                .or(() -> roleRepository.findAll().stream()
                        .filter(r -> r.getName().equalsIgnoreCase("ROLE_SUPERUSER"))
                        .findFirst())
                .orElse(null);

        if (superuserRole == null) {
            log.info(">>> ROLE_SUPERUSER no encontrado, intentando crear o migrar...");
            superuserRole = roleRepository.findByName("ROLE_ADMIN").orElse(null);
            if (superuserRole != null) {
                log.info(">>> Migrando ROLE_ADMIN a ROLE_SUPERUSER...");
                superuserRole.setName("ROLE_SUPERUSER");
                superuserRole.setDescription("Superusuario con acceso total");
            } else {
                superuserRole = Role.builder()
                        .name("ROLE_SUPERUSER")
                        .description("Superusuario con acceso total")
                        .build();
            }
            try {
                superuserRole = roleRepository.saveAndFlush(superuserRole);
            } catch (Exception e) {
                superuserRole = roleRepository.findAll().stream()
                        .filter(r -> r.getName().equalsIgnoreCase("ROLE_SUPERUSER"))
                        .findFirst().orElseThrow();
            }
        }
        superuserRole.setPermissions(allPermissions);
        superuserRole = roleRepository.saveAndFlush(superuserRole);

        // 2.2 ROLE_ADMIN (Limitado)
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .or(() -> roleRepository.findAll().stream()
                        .filter(r -> r.getName().equalsIgnoreCase("ROLE_ADMIN"))
                        .findFirst())
                .orElse(null);

        if (adminRole == null) {
            log.info(">>> ROLE_ADMIN no encontrado, creando...");
            adminRole = Role.builder()
                    .name("ROLE_ADMIN")
                    .description("Administrador con restricciones de borrado")
                    .build();
            try {
                adminRole = roleRepository.saveAndFlush(adminRole);
            } catch (Exception e) {
                adminRole = roleRepository.findAll().stream()
                        .filter(r -> r.getName().equalsIgnoreCase("ROLE_ADMIN"))
                        .findFirst().orElseThrow();
            }
        }
        adminRole.setPermissions(limitedPermissions);
        adminRole = roleRepository.saveAndFlush(adminRole);

        // 3. Crear o actualizar Usuarios
        setupUser("superuser", "superuser@sgd.com", "Super", "User", "Admin1234!", "CI", "0000000", superuserRole);
        setupUser("admin", "admin@sgd.com", "Admin", "Limited", "Admin1234!", "CI", "1111111", adminRole);

        log.info(">>> DataInitializer finalizado correctamente.");
    }

    private void setupUser(String username, String email, String first, String last, String pass, String docType, String docNum, Role role) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            log.info(">>> Actualizando roles para el usuario: {}", username);
            user.setRoles(new HashSet<>(Set.of(role)));
            // Sincronizamos campos extra si faltan
            if (user.getDocumentNumber() == null) {
                user.setDocumentType(docType);
                user.setDocumentNumber(docNum);
            }
            userRepository.saveAndFlush(user);
        } else {
            log.info(">>> Creando nuevo usuario: {}", username);
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
                    .build());
        }
    }

    private Set<Permission> createDefaultPermissions() {
        List<String> permissionNames = List.of(
            "USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DELETE",
            "ROLE_READ", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE",
            "PERMISSION_READ", "PERMISSION_CREATE", "PERMISSION_UPDATE", "PERMISSION_DELETE",
            "PATIENT_READ", "PATIENT_CREATE", "PATIENT_UPDATE", "PATIENT_DELETE"
        );

        // Cargamos todos los nombres existentes para comparar de forma segura
        Set<String> existingNames = new HashSet<>(permissionRepository.findAll().stream()
                .map(p -> p.getName().toUpperCase().trim())
                .toList());

        Set<Permission> permissions = new HashSet<>();
        for (String name : permissionNames) {
            String upperName = name.toUpperCase().trim();
            
            Permission p;
            if (existingNames.contains(upperName)) {
                p = permissionRepository.findByName(name)
                        .or(() -> permissionRepository.findAll().stream()
                                .filter(perm -> perm.getName().equalsIgnoreCase(upperName))
                                .findFirst())
                        .orElse(null);
            } else {
                try {
                    log.info(">>> Creando permiso: {}", name);
                    p = permissionRepository.saveAndFlush(Permission.builder()
                            .name(name)
                            .module("SYSTEM")
                            .action(name)
                            .description("Permiso para " + name)
                            .build());
                } catch (Exception e) {
                    log.warn(">>> El permiso {} ya parece existir o hubo un conflicto: {}", name, e.getMessage());
                    p = permissionRepository.findByName(name).orElse(null);
                }
            }
            
            if (p != null) permissions.add(p);
        }
        return permissions;
    }
}