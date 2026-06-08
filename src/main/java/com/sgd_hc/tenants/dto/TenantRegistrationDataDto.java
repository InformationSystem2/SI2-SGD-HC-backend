package com.sgd_hc.tenants.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantRegistrationDataDto {
    private String tenantName;
    private String adminFirstName;
    private String adminLastName;
    private String adminEmail;
    private String adminPassword;
    private String adminPhone;
    private String adminDocumentType;
    private String adminDocumentNumber;
    private String adminGender;
}
