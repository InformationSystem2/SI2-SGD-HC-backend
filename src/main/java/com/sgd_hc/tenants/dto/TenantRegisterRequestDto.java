package com.sgd_hc.tenants.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para el inicio del onboarding (Registro inicial).
 * Crea el Tenant (congelado) y el Usuario Admin (inactivo).
 */
public record TenantRegisterRequestDto(
        @NotBlank @Size(max = 200) String tenantName,
        

        @NotBlank @Email @Size(max = 100) String adminEmail,
        @NotBlank @Size(max = 100) String adminFirstName,
        @NotBlank @Size(max = 100) String adminLastName,
        @NotBlank @Size(min = 8)    String adminPassword,
        
        @Size(max = 20) String adminPhone,
        @NotBlank String adminDocumentType,
        @NotBlank @Size(max = 15) String adminDocumentNumber,
        @NotBlank String adminGender,
        String selectedPlan
) {}
