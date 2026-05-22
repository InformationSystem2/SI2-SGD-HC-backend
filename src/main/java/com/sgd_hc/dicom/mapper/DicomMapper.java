package com.sgd_hc.dicom.mapper;

import com.sgd_hc.dicom.dto.DicomInstanceDto;
import com.sgd_hc.dicom.dto.DicomSeriesDto;
import com.sgd_hc.dicom.dto.DicomStudyDto;
import com.sgd_hc.dicom.entity.DicomInstance;
import com.sgd_hc.dicom.entity.DicomSeries;
import com.sgd_hc.dicom.entity.DicomStudy;
import org.springframework.stereotype.Component;

import java.util.List;

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
}
