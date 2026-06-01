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
 * <p>
 * Se ejecuta por cada tenant configurado: si un tenant ya tiene plantillas,
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
            List<Tenant> tenants = tenantRepository.findAll();
            for (Tenant tenant : tenants) {
                seedForTenant(tenant);
            }
        } finally {
            TenantContext.clear();
        }
    }

    public void seedForTenant(Tenant tenant) {
        if (tenant == null) {
            log.warn(">>> TemplateDataSeeder: tenant nulo, se omite.");
            return;
        }

        List<DocumentTemplate> existing = templateRepository.findByTenantIdAndIsActiveTrue(tenant.getId());

        if (!existing.isEmpty()) {
            log.info(">>> TemplateDataSeeder: tenant '{}' ya tiene {} plantilla(s), se omite.",
                    tenant.getSlug(), existing.size());
            return;
        }

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
                                        "B_POS", "B Positivo"),
                                Map.of() // sin subSchema
                        ),
                        "observaciones", new FieldConfig(TEXTAREA, false, "Notas", 2)),
                tenant));

        templateRepository.save(buildTemplate(
                "Receta Médica Estándar (Multi-Medicamento)",
                "Plantilla oficial que permite la prescripción de uno o múltiples medicamentos dentro de una lista dinámica.",
                Map.ofEntries(
                        Map.entry("diagnostico_principal", new FieldConfig(TEXT, true, "Diagnóstico Principal", 1)),

                        Map.entry("medicamentos_recetados", new FieldConfig(
                                ARRAY,
                                true,
                                "Lista de Medicamentos Prescritos",
                                2,
                                Map.of(),
                                Map.ofEntries(
                                        Map.entry("nombre_medicamento", new FieldConfig(TEXT, true, "Medicamento", 1)),
                                        Map.entry("dosis", new FieldConfig(TEXT, true, "Dosis y Presentación (Ej. 500mg, Comprimido)", 2)),
                                        Map.entry("frecuencia", new FieldConfig(
                                                SELECT,
                                                true,
                                                "Frecuencia de Toma",
                                                3,
                                                Map.ofEntries(
                                                        Map.entry("CADA_4_HRS", "Cada 4 horas"),
                                                        Map.entry("CADA_6_HRS", "Cada 6 horas"),
                                                        Map.entry("CADA_8_HRS", "Cada 8 horas"),
                                                        Map.entry("CADA_12_HRS", "Cada 12 horas"),
                                                        Map.entry("CADA_24_HRS", "Una vez al día (24 hrs)"),
                                                        Map.entry("CONDICIONAL", "Condicional al dolor o fiebre")
                                                ),
                                                null
                                        )),
                                        Map.entry("duracion", new FieldConfig(NUMBER, true, "Duración (Días)", 4))
                                )
                        )),

                        Map.entry("indicaciones_adicionales", new FieldConfig(TEXTAREA, false, "Indicaciones Generales / Recomendaciones de Cuidado", 3))
                ),
                tenant
        ));

        
        templateRepository.save(buildTemplate(
                "Orden de Imagenología",
                "Solicitud de estudios radiológicos y de diagnóstico por imagen.",
                Map.of(
                        "diagnostico_principal", new FieldConfig(TEXTAREA, true, "Diagnóstico Principal", 1),
                        "estudio_solicitado",
                        new FieldConfig(TEXT, true, "Estudio Solicitado (Ej. Radiografía de Tórax)", 2),
                        "indicacion_clinica",
                        new FieldConfig(TEXTAREA, true, "Indicación Clínica / Motivo del Estudio", 3),
                        "notas_adicionales", new FieldConfig(TEXTAREA, false, "Notas Adicionales", 4)),
                tenant));
        templateRepository.save(buildTemplate(
                "Nota de Alta",
                "Resumen oficial de la evolución, tratamiento y recomendaciones al momento de la salida del paciente.",
                Map.ofEntries(
                        Map.entry("fecha_ingreso", new FieldConfig(DATE, true, "Fecha de Ingreso", 1)),
                        Map.entry("fecha_alta", new FieldConfig(DATE, true, "Fecha de Alta", 2)),

                        Map.entry("diagnostico_principal", new FieldConfig(TEXT, true, "Diagnóstico Principal", 3)),
                        Map.entry("diagnostico_alta", new FieldConfig(TEXT, true, "Diagnóstico de Alta", 4)),
                        Map.entry("diagnosticos_secundarios", new FieldConfig(TEXTAREA, false, "Diagnósticos Secundarios", 5)),

                        Map.entry("procedimientos_realizados", new FieldConfig(TEXTAREA, false, "Procedimientos Realizados", 6)),
                        Map.entry("evolucion_clinica", new FieldConfig(TEXTAREA, true, "Evolución Clínica", 7)),
                        Map.entry("resumen_tratamiento", new FieldConfig(TEXTAREA, true, "Resumen del Tratamiento", 8)),
                        Map.entry("medicinas_alta", new FieldConfig(TEXTAREA, true, "Medicinas al Alta", 9)),

                        Map.entry("restricciones_actividad", new FieldConfig(TEXTAREA, false, "Restricciones de Actividad", 10)),
                        Map.entry("actividades_permitidas", new FieldConfig(TEXTAREA, false, "Actividades Permitidas", 11)),
                        Map.entry("restricciones_dieteticas", new FieldConfig(TEXTAREA, false, "Restricciones Dietéticas", 12)),
                        Map.entry("cuidados_heridas", new FieldConfig(TEXTAREA, false, "Cuidados de Heridas", 13)),

                        Map.entry("instrucciones_seguimiento", new FieldConfig(TEXTAREA, true, "Instrucciones de Seguimiento", 14)),
                        Map.entry("fecha_recomendada_retorno", new FieldConfig(DATE, false, "Fecha Recomendada para Retorno", 15))
                ),
                tenant
        ));

        log.info(">>> TemplateDataSeeder: plantillas predefinidas creadas exitosamente para el tenant '{}'.", tenant.getSlug());
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
