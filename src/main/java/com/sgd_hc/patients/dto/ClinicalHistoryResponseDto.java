package com.sgd_hc.patients.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sgd_hc.patients.entity.Allergy;
import com.sgd_hc.patients.entity.Medication;
import lombok.Builder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClinicalHistoryResponseDto(
        UUID id,
        UUID patientId,
        String code,
        String bloodType,
        String pathologicalAntecedents,
        String nonPathologicalAntecedents,
        String familyAntecedents,
        List<Allergy> allergies,
        String chronicConditions,
        List<Medication> currentMedications,
        String observations,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
