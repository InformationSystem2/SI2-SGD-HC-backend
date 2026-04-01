package com.sgd_hc.sgd_hc.module_users.dto;

import java.util.Set;

public record UserResponseDto(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        String documentType,
        String documentNumber,
        Boolean gender,
        String isActive,
        Set<Long> rolesIds
) {

}
