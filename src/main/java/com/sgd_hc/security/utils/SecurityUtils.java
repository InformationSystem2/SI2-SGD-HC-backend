package com.sgd_hc.security.utils;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

public class SecurityUtils {

    public static Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return Set.of();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    public static void requireAuthority(Set<String> authorities, String authority) {
        if (!authorities.contains(authority))
            throw new AccessDeniedException("Missing authority: " + authority);
    }
}
