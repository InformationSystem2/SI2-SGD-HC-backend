package com.sgd_hc.patients.mapper;

import com.sgd_hc.patients.dto.ClinicalHistoryCreateDto;
import com.sgd_hc.patients.dto.ClinicalHistoryResponseDto;
import com.sgd_hc.patients.dto.ClinicalHistoryUpdateDto;
import com.sgd_hc.patients.entity.ClinicalHistory;
import com.sgd_hc.patients.entity.Patient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ClinicalHistoryMapper {

    public ClinicalHistory toEntity(ClinicalHistoryCreateDto dto, Patient patient) {
        ClinicalHistory history = new ClinicalHistory();
        history.setPatient(patient);
        history.setCode(dto.code());
        history.setBloodType(dto.bloodType());
        history.setPathologicalAntecedents(dto.pathologicalAntecedents());
        history.setNonPathologicalAntecedents(dto.nonPathologicalAntecedents());
        history.setFamilyAntecedents(dto.familyAntecedents());
        history.setAllergies(dto.allergies());
        history.setChronicConditions(dto.chronicConditions());
        history.setCurrentMedications(dto.currentMedications());
        history.setObservations(dto.observations());
        return history;
    }

    public void updateEntityFromDto(ClinicalHistoryUpdateDto dto, ClinicalHistory history) {
        if (dto.bloodType() != null) history.setBloodType(dto.bloodType());
        if (dto.pathologicalAntecedents() != null) history.setPathologicalAntecedents(dto.pathologicalAntecedents());
        if (dto.nonPathologicalAntecedents() != null) history.setNonPathologicalAntecedents(dto.nonPathologicalAntecedents());
        if (dto.familyAntecedents() != null) history.setFamilyAntecedents(dto.familyAntecedents());
        if (dto.allergies() != null) history.setAllergies(dto.allergies());
        if (dto.chronicConditions() != null) history.setChronicConditions(dto.chronicConditions());
        if (dto.currentMedications() != null) history.setCurrentMedications(dto.currentMedications());
        if (dto.observations() != null) history.setObservations(dto.observations());
    }

    public ClinicalHistoryResponseDto toResponseDto(ClinicalHistory history) {
        if (history == null) return null;
        return ClinicalHistoryResponseDto.builder()
                .id(history.getId())
                .patientId(history.getPatient() != null ? history.getPatient().getId() : null)
                .code(history.getCode())
                .bloodType(history.getBloodType())
                .pathologicalAntecedents(history.getPathologicalAntecedents())
                .nonPathologicalAntecedents(history.getNonPathologicalAntecedents())
                .familyAntecedents(history.getFamilyAntecedents())
                .allergies(history.getAllergies())
                .chronicConditions(history.getChronicConditions())
                .currentMedications(history.getCurrentMedications())
                .observations(history.getObservations())
                .createdAt(history.getCreatedAt())
                .updatedAt(history.getUpdatedAt())
                .build();
    }

    public Map<String, Object> toAuditMap(ClinicalHistory history) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", history.getId() != null ? history.getId().toString() : null);
        map.put("patientId", history.getPatient() != null ? history.getPatient().getId().toString() : null);
        map.put("code", history.getCode());
        map.put("bloodType", history.getBloodType());
        map.put("createdAt", history.getCreatedAt() != null ? history.getCreatedAt().toString() : null);
        map.put("updatedAt", history.getUpdatedAt() != null ? history.getUpdatedAt().toString() : null);
        return map;
    }
}
