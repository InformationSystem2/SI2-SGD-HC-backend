package com.sgd_hc.documents;

import com.sgd_hc.documents.dto.FieldConfig;
import com.sgd_hc.documents.entity.DocumentTemplate;
import com.sgd_hc.documents.repository.DocumentTemplateRepository;
import com.sgd_hc.security.config.tenant.TenantContext;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.sgd_hc.documents.entity.FieldType.*;

/**
 * siembra las plantillas clínicas predefinidas al arrancar la aplicación.
 *
 * <p>Se ejecuta por cada tenant configurado: si un tenant ya tiene plantillas,
 * se omite la siembra para ese tenant.
 * Esto garantiza que tanto el superusuario (tenant "system") como el admin
 * (tenant "default") vean plantillas al iniciar el sistema.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateDataSeeder {

    private final DocumentTemplateRepository templateRepository;
    private final TenantRepository tenantRepository;

    @Value("${app.seed.system.slug:system}")
    private String systemTenantSlug;

    @Value("${app.seed.default.slug:default}")
    private String defaultTenantSlug;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedTemplates() {
        log.info(">>> TemplateDataSeeder: verificando plantillas predefinidas...");
        TenantContext.setBypassFilter(true);

        try {
            seedForSlug(systemTenantSlug);
            seedForSlug(defaultTenantSlug);
        } finally {
            TenantContext.clear();
        }
    }

    private void seedForSlug(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug).orElse(null);
        if (tenant == null) {
            log.warn(">>> TemplateDataSeeder: tenant '{}' no encontrado, se omite.", slug);
            return;
        }

        List<DocumentTemplate> existing =
                templateRepository.findByTenantIdAndIsActiveTrue(tenant.getId());

        if (!existing.isEmpty()) {
            log.info(">>> TemplateDataSeeder: tenant '{}' ya tiene {} plantilla(s), se omite.",
                    slug, existing.size());
            return;
        }

        templateRepository.save(buildTemplate(
                "Receta Médica Estándar",
                "Plantilla estándar para emisión de recetas médicas",
                Map.of(
                        "medicamento", new FieldConfig(TEXT, true, "Medicamento", 1),
                        "dosis", new FieldConfig(TEXT, false, "Dosis", 2),
                        "indicaciones", new FieldConfig(TEXTAREA, false, "Indicaciones", 3)
                ),
                tenant
        ));

        templateRepository.save(buildTemplate(
                "Evolución Clínica",
                "Registro de evolución del paciente durante la consulta",
                Map.of(
                        "motivo_consulta", new FieldConfig(TEXTAREA, true, "Motivo de Consulta", 1),
                        "presion_arterial", new FieldConfig(TEXT, false, "Presión Arterial", 2),
                        "diagnostico", new FieldConfig(TEXTAREA, false, "Diagnóstico", 3)
                ),
                tenant
        ));

        templateRepository.save(buildTemplate(
    "Ficha de Ingreso",
    "Datos básicos de admisión",
    Map.of(
        "tipo_sangre", new FieldConfig(
            SELECT, 
            true, 
            "Grupo Sanguíneo", 
            1,
            Map.of(
                "O_POS", "O Positivo",
                "O_NEG", "O Negativo",
                "A_POS", "A Positivo",
                "B_POS", "B Positivo"
            )
        ),
        "observaciones", new FieldConfig(TEXTAREA, false, "Notas", 2)
    ),
    tenant
));

        log.info(">>> TemplateDataSeeder: 3 plantillas creadas exitosamente para el tenant '{}'.", slug);
    }

    private DocumentTemplate buildTemplate(String name, String description,
                                           Map<String, FieldConfig> uiSchema, Tenant tenant) {
        DocumentTemplate t = new DocumentTemplate();
        t.setName(name);
        t.setDescription(description);
        t.setUiSchema(uiSchema);
        t.setIsActive(true);
        t.setTenant(tenant);
        return t;
    }
}
