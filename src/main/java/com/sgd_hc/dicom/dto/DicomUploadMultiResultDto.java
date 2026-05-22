package com.sgd_hc.dicom.dto;

import java.util.List;

/**
 * Respuesta del endpoint {@code POST /dicom/upload-multi}.
 *
 * <p>El conjunto de archivos puede pertenecer a uno o varios {@code StudyInstanceUID}.
 * El campo {@code study} contiene el primer estudio que recibió instancias nuevas,
 * con su árbol completo (Series → Instances), para que el cliente pueda abrir el
 * visor inmediatamente. Si todo se omitió (todos duplicados o errores), es
 * {@code null}.
 *
 * <p>Las listas son disjuntas y siempre presentes (posiblemente vacías).
 */
public record DicomUploadMultiResultDto(
        DicomStudyDto study,
        List<DicomUploadMultiItemDto> uploaded,
        List<DicomUploadMultiItemDto> skipped,
        List<DicomUploadMultiItemDto> errors
) {}
