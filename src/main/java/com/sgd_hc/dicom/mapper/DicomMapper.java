package com.sgd_hc.dicom.mapper;

import com.sgd_hc.dicom.dto.DicomInstanceDto;
import com.sgd_hc.dicom.dto.DicomSeriesDto;
import com.sgd_hc.dicom.dto.DicomStudyDto;
import com.sgd_hc.dicom.dto.DicomUploadMultiResultDto;
import com.sgd_hc.dicom.entity.DicomInstance;
import com.sgd_hc.dicom.entity.DicomSeries;
import com.sgd_hc.dicom.entity.DicomStudy;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
public class DicomMapper {

    public DicomStudyDto toStudyDto(DicomStudy study) {
        List<DicomSeriesDto> seriesDtos = study.getSeries().stream()
                .map(this::toSeriesDto)
                .toList();

        return new DicomStudyDto(
                study.getId(),
                study.getPatientId(),
                study.getUploaderId(),
                study.getStudyInstanceUid(),
                study.getStudyDate(),
                study.getStudyDescription(),
                study.getAccessionNumber(),
                seriesDtos
        );
    }

    public DicomSeriesDto toSeriesDto(DicomSeries series) {
        List<DicomInstanceDto> instanceDtos = series.getInstances().stream()
                .map(this::toInstanceDto)
                .toList();

        return new DicomSeriesDto(
                series.getId(),
                series.getSeriesInstanceUid(),
                series.getModality(),
                series.getSeriesNumber(),
                series.getSeriesDescription(),
                series.getBodyPart(),
                instanceDtos
        );
    }

    public DicomInstanceDto toInstanceDto(DicomInstance instance) {
        return new DicomInstanceDto(
                instance.getId(),
                instance.getSopInstanceUid(),
                instance.getInstanceNumber(),
                instance.getRows(),
                instance.getColumns(),
                instance.getBitsAllocated(),
                instance.getWindowCenter(),
                instance.getWindowWidth(),
                instance.getPixelSpacing()
        );
    }

    public Map<String, Object> toAuditMap(DicomStudy entity) {
        if (entity == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId().toString());
        map.put("studyInstanceUid", entity.getStudyInstanceUid());
        map.put("patientId", entity.getPatientId() != null ? entity.getPatientId().toString() : null);
        map.put("uploaderId", entity.getUploaderId() != null ? entity.getUploaderId().toString() : null);
        map.put("tenantId", entity.getTenant() != null ? entity.getTenant().getId().toString() : null);
        map.put("studyDate", entity.getStudyDate() != null ? entity.getStudyDate().toString() : null);
        map.put("studyDescription", entity.getStudyDescription());
        map.put("accessionNumber", entity.getAccessionNumber());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }

    public Map<String, Object> toAuditMapFromMultiResult(DicomUploadMultiResultDto dto) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("studyId", dto.study() != null ? dto.study().id() : null);
        map.put("uploadedCount", dto.uploaded() != null ? dto.uploaded().size() : 0);
        map.put("skippedCount", dto.skipped() != null ? dto.skipped().size() : 0);
        map.put("errorsCount", dto.errors() != null ? dto.errors().size() : 0);
        return map;
    }
}
