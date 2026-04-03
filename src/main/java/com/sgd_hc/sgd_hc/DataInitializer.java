package com.sgd_hc.sgd_hc;

import com.sgd_hc.sgd_hc.module_users.entity.Role;
import com.sgd_hc.sgd_hc.module_users.entity.User;
import com.sgd_hc.sgd_hc.module_users.repository.RoleRepository;
import com.sgd_hc.sgd_hc.module_users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        // 1. Crear rol ADMIN si no existe
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> {
                    log.info(">>> Creando rol ROLE_ADMIN...");
                    return roleRepository.save(
                            Role.builder()
                                    .name("ROLE_ADMIN")
                                    .description("Administrador del sistema")
                                    .build()
                    );
                });

        // 2. Crear usuario admin si no existe
        if (!userRepository.existsByUsername("admin")) {
            log.info(">>> Creando usuario admin...");
            User admin = User.builder()
                    .username("admin")
                    .email("admin@sgd.com")
                    .firstName("Administrador")
                    .lastName("Sistema")
                    .password(passwordEncoder.encode("Admin1234!"))
                    .gender("M")
                    .isActive(true)
                    .roles(Set.of(adminRole))
                    .build();

            userRepository.save(admin);
            log.info(">>> Usuario admin creado. Username: admin | Password: Admin1234!");
        } else {
            log.info(">>> Usuario admin ya existe, omitiendo.");
        }
    }
}