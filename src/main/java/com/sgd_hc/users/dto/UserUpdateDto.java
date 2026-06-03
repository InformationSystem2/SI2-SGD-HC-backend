package com.sgd_hc.users.dto;

import java.util.Set;

public record UserUpdateDto(
        String documentType,
        String documentNumber,
        String email,
        String firstName,
        String lastName,
        String password,
        String phone,
        String gender,
        Boolean isActive,
        Set<Long> rolesIds
) {
}
