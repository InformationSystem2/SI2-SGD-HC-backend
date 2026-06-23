package com.sgd_hc.patients.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estructura embebida (JSONB) para una alergia dentro de la historia clínica.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Allergy {
    /** Sustancia que provoca la alergia. Ej: Penicilina */
    private String allergen;
    /** Severidad. Ej: Alta, Media, Baja */
    private String severity;
    /** Reacción que provoca. */
    private String reaction;
}
