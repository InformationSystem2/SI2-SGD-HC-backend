package com.sgd_hc.security.service;

import com.sgd_hc.security.config.tenant.TenantContext;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.tenants.entity.SubscriptionStatus;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user;
        // 1. Activar bypass para buscar al usuario en TODA la base de datos 
        TenantContext.setBypassFilter(true);
        try {
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario '" + username + "' no encontrado."));
            
            // 2. Validaciones de estado del usuario
            if (!user.getIsActive()) {
                throw new DisabledException("Su cuenta de usuario está desactivada.");
            }

            if (user.getTenant() != null) {
                UUID tenantId = user.getTenant().getId();
                String tenantSlug = user.getTenant().getSlug();
                SubscriptionStatus status = user.getTenant().getSubscriptionStatus();

                // 3. Activar el contexto de su clínica automáticamente
                TenantContext.setCurrentTenantId(tenantId);
                TenantContext.setCurrentTenantSlug(tenantSlug);

                // 4. Validaciones de suscripción del tenant
                if (status == SubscriptionStatus.PENDING_PAYMENT) {
                    throw new DisabledException("La suscripción de su clínica está pendiente de pago. Por favor complete el proceso de pago.");
                }
                if (status != SubscriptionStatus.ACTIVE) {
                    throw new DisabledException("La suscripción de su clínica no está activa.");
                }
            }

            // Forzar inicialización de colecciones Lazy (Roles y Permisos)
            if (user.getRoles() != null) {
                user.getRoles().forEach(role -> {
                    if (role.getPermissions() != null) role.getPermissions().size();
                });
            }

        } finally {
            TenantContext.setBypassFilter(false);
        }

        return new SecurityUser(user);
    }
}
