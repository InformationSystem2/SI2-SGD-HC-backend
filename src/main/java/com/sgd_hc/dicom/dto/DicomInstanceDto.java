package com.sgd_hc.dicom.dto;

import java.util.UUID;

public record DicomInstanceDto(
        UUID id,
        String sopInstanceUid,
        Integer instanceNumber,
        Integer rows,
        Integer columns,
        Integer bitsAllocated,
        Double windowCenter,
        Double windowWidth,
        Double[] pixelSpacing
) {}
