package com.sgd_hc.documents.dto;

import java.util.List;

/**
 * Payload que envía OnlyOffice Document Server a nuestro callbackUrl.
 *
 * <p>Códigos de status relevantes:
 * <ul>
 *   <li>1 – Documento siendo editado (no hacer nada).</li>
 *   <li>2 – Listo para guardar; {@code url} contiene el archivo final.</li>
 *   <li>4 – Cerrado sin cambios (no hacer nada).</li>
 *   <li>6 – Forzar guardado; {@code url} contiene el archivo.</li>
 * </ul>
 */
public record OnlyofficeCallbackDto(
        String key,
        int status,
        String url,
        List<String> users
) {}
