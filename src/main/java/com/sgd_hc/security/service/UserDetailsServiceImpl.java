package com.sgd_hc.security.service;

import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.repository.UserRepository;
import com.sgd_hc.security.details.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Forzar la inicialización de roles y sus permisos dentro de la sesión
        if (user.getRoles() != null) {
            user.getRoles().forEach(role -> {
                if (role.getPermissions() != null) {
                    role.getPermissions().size();
                }
            });
        }

        return new SecurityUser(user);
    }
}
