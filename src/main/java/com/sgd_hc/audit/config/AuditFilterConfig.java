package com.sgd_hc.audit.config;

import com.sgd_hc.audit.filter.AuditLogFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AuditFilterConfig {

    private final AuditLogFilter auditLogFilter;

    @Bean
    public FilterRegistrationBean<AuditLogFilter> auditFilterRegistration() {
        FilterRegistrationBean<AuditLogFilter> registration =
                new FilterRegistrationBean<>(auditLogFilter);
        registration.setEnabled(false);
        return registration;
    }
}
