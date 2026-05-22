package com.sgd_hc.dicom.dto;

import com.sgd_hc.dicom.entity.Modality;

import java.util.List;
import java.util.UUID;

public record DicomSeriesDto(
        UUID id,
        String seriesInstanceUid,
        Modality modality,
        Integer seriesNumber,
        String seriesDescription,
        String bodyPart,
        List<DicomInstanceDto> instances
) {}
