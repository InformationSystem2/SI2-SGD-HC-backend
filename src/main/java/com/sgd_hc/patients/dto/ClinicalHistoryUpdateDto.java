package com.sgd_hc.patients.dto;

import com.sgd_hc.patients.entity.Allergy;
import com.sgd_hc.patients.entity.Medication;
import java.util.List;

public record ClinicalHistoryUpdateDto(
        String bloodType,
        String pathologicalAntecedents,
        String nonPathologicalAntecedents,
        String familyAntecedents,
        List<Allergy> allergies,
        String chronicConditions,
        List<Medication> currentMedications,
        String observations
) {}
