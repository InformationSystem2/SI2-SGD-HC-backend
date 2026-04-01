package com.sgd_hc.sgd_hc.module_users.dto;

import java.util.Set;

public record UserUpdateDto(
        String documentType,
        String documentNumber,
        String firstName,
        String lastName,
        String password,
        String phone,
        Boolean isActive,
        Set<Long> rolesIds
) {

}
