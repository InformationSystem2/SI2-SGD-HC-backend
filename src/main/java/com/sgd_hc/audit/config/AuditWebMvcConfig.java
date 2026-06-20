package com.sgd_hc.audit.config;

import com.sgd_hc.audit.interceptor.AuditLogInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AuditWebMvcConfig implements WebMvcConfigurer {

    private final AuditLogInterceptor auditLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditLogInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/schema/**",
                        "/api-docs/**",
                        "/swagger**",
                        "/health",
                        "/metrics"
                );
    }
}
