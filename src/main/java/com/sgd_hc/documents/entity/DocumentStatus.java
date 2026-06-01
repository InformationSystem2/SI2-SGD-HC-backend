package com.sgd_hc.documents.entity;

/**
 * Estados del ciclo de vida médico-legal de un {@link Document}.
 *
 * <p>Transiciones válidas (ver {@code WorkflowService}):
 * <ul>
 *   <li>{@link #DRAFT} → {@link #PENDING_REVIEW}</li>
 *   <li>{@link #PENDING_REVIEW} → {@link #REJECTED} | {@link #FINALIZED}</li>
 *   <li>{@link #REJECTED} → {@link #DRAFT} (para corrección) | {@link #PENDING_REVIEW}</li>
 *   <li>{@link #FINALIZED} → <b>(terminal — inmutable)</b></li>
 * </ul>
 */
public enum DocumentStatus {

    /** Borrador editable por el autor. */
    DRAFT,

    /** Enviado a revisión, en espera de aprobación o rechazo. */
    PENDING_REVIEW,

    /** Rechazado por el revisor, requiere corrección por el autor. */
    REJECTED,

    /** Firmado/bloqueado. Estado terminal, jamás puede volver a {@link #DRAFT}. */
    FINALIZED
}
