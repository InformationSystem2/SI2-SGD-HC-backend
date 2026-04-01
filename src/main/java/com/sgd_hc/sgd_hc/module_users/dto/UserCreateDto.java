package com.sgd_hc.sgd_hc.module_users.dto;

import java.util.Set;

public record UserCreateDto(
        String documentType,
        String documentNumber,

        String email,
        String firstName,
        String lastName,
        String password,
        String phone,
        String gender,
        Set<Long> rolesIds
) {}