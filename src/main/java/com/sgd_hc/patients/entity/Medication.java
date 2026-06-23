package com.sgd_hc.patients.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estructura embebida (JSONB) para un medicamento actual dentro de la historia clínica.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Medication {
    /** Nombre del medicamento. Ej: Metformina */
    private String medication;
    /** Dosis. Ej: 500mg */
    private String dose;
    /** Frecuencia de toma. */
    private String frequency;
}
