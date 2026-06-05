package com.sgd_hc.tenants.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendCodeResponseDto {
    private String message;
    private String code; // Only populated in 'dev' environment
}
