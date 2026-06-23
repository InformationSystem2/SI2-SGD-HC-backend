package com.sgd_hc.patients.service;

import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.service.AuditableService;
import com.sgd_hc.patients.dto.ClinicalHistoryCreateDto;
import com.sgd_hc.patients.dto.ClinicalHistoryResponseDto;
import com.sgd_hc.patients.dto.ClinicalHistoryUpdateDto;
import com.sgd_hc.patients.entity.ClinicalHistory;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.patients.mapper.ClinicalHistoryMapper;
import com.sgd_hc.patients.repository.ClinicalHistoryRepository;
import com.sgd_hc.patients.repository.PatientRepository;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.TenantResolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClinicalHistoryService implements AuditableService<UUID, ClinicalHistory> {

    private final ClinicalHistoryRepository clinicalHistoryRepository;
    private final PatientRepository patientRepository;
    private final ClinicalHistoryMapper clinicalHistoryMapper;
    private final TenantResolverService tenantResolverService;
    private final DocumentRepository documentRepository;

    @Transactional
    @Auditable(resourceType = "CLINICAL_HISTORY", actionType = ActionType.CREATE)
    public ClinicalHistoryResponseDto create(ClinicalHistoryCreateDto dto) {
        if (clinicalHistoryRepository.existsByPatientId(dto.patientId())) {
            throw new IllegalArgumentException("El paciente ya tiene una historia clínica activa.");
        }
        if (clinicalHistoryRepository.existsByCode(dto.code())) {
            throw new IllegalArgumentException("El código de historia clínica ya se encuentra registrado.");
        }

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró al paciente con id: " + dto.patientId()));

        Tenant tenant = tenantResolverService.resolve();

        ClinicalHistory history = clinicalHistoryMapper.toEntity(dto, patient);
        history.setTenant(tenant);

        ClinicalHistory saved = clinicalHistoryRepository.saveAndFlush(history);

        // Vincular retroactivamente los documentos previos del paciente que aún
        // no estén asociados a ninguna historia clínica.
        documentRepository.linkOrphanDocumentsToClinicalHistory(patient.getId(), saved.getId());

        return clinicalHistoryMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    @Auditable(resourceType = "CLINICAL_HISTORY", actionType = ActionType.READ, idParamName = "id")
    public ClinicalHistoryResponseDto getById(UUID id) {
        return clinicalHistoryMapper.toResponseDto(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    @Auditable(resourceType = "CLINICAL_HISTORY", actionType = ActionType.READ, idParamName = "patientId")
    public ClinicalHistoryResponseDto getByPatientId(UUID patientId) {
        ClinicalHistory history = clinicalHistoryRepository.findByPatientId(patientId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la historia clínica para el paciente con id: " + patientId));
        return clinicalHistoryMapper.toResponseDto(history);
    }

    @Transactional
    @Auditable(resourceType = "CLINICAL_HISTORY", actionType = ActionType.UPDATE, idParamName = "id")
    public ClinicalHistoryResponseDto update(UUID id, ClinicalHistoryUpdateDto dto) {
        ClinicalHistory history = findOrThrow(id);
        clinicalHistoryMapper.updateEntityFromDto(dto, history);
        return clinicalHistoryMapper.toResponseDto(clinicalHistoryRepository.save(history));
    }

    @Transactional
    @Auditable(resourceType = "CLINICAL_HISTORY", actionType = ActionType.DELETE, idParamName = "id")
    public void delete(UUID id) {
        clinicalHistoryRepository.deleteById(id);
    }

    private ClinicalHistory findOrThrow(UUID id) {
        return clinicalHistoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la historia clínica con id: " + id));
    }

    @Override
    public ClinicalHistory getEntity(UUID id) {
        return findOrThrow(id);
    }

    @Override
    public Map<String, Object> toAuditMap(ClinicalHistory entity) {
        return clinicalHistoryMapper.toAuditMap(entity);
    }
}
