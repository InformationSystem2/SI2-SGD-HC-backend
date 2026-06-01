package com.sgd_hc.dicom.dto;

/**
 * Resultado individual de un archivo dentro de un lote multi-upload DICOM.
 *
 * @param filename       Nombre original del archivo enviado por el cliente.
 * @param sopInstanceUid SOPInstanceUID extraído del header (puede ser {@code null}
 *                       si el archivo ni siquiera pudo parsearse como DICOM).
 * @param reason         Motivo no-vacío para entradas en {@code skipped} o
 *                       {@code errors}; {@code null} para entradas en {@code uploaded}.
 *                       Valores típicos: {@code "duplicate"}, {@code "invalid-dicom"},
 *                       {@code "missing-tag"}, {@code "io-error"}.
 */
public record DicomUploadMultiItemDto(
        String filename,
        String sopInstanceUid,
        String reason
) {}
