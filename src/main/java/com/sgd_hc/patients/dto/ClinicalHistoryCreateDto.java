package com.sgd_hc.patients.dto;

import com.sgd_hc.patients.entity.Allergy;
import com.sgd_hc.patients.entity.Medication;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ClinicalHistoryCreateDto(
        @NotNull(message = "El paciente es obligatorio")
        UUID patientId,

        @NotBlank(message = "El código es obligatorio")
        String code,

        String bloodType,
        String pathologicalAntecedents,
        String nonPathologicalAntecedents,
        String familyAntecedents,
        List<Allergy> allergies,
        String chronicConditions,
        List<Medication> currentMedications,
        String observations
) {}
