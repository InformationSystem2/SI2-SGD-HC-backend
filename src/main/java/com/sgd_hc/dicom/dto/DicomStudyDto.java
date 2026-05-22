package com.sgd_hc.dicom.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DicomStudyDto(
        UUID id,
        UUID patientId,
        UUID uploaderId,
        String studyInstanceUid,
        LocalDate studyDate,
        String studyDescription,
        String accessionNumber,
        List<DicomSeriesDto> series
) {}
