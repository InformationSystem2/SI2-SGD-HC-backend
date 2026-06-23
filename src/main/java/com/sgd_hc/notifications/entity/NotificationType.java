package com.sgd_hc.notifications.entity;

public enum NotificationType {
    // Tareas y flujos de revisión originales
    TASK_ASSIGNED,
    TASK_COMPLETED,
    TASK_APPROVED,
    TASK_REJECTED,
    TASK_OVERDUE,
    DOC_REVIEW,
    DOC_FINALIZED,
    DOC_REJECTED,

    // Suscripción y Multi-tenancy
    TENANT_SUBSCRIPTION_EXPIRING,
    TENANT_STORAGE_LIMIT_ALERT,
    TENANT_PAYMENT_FAILED,

    // Recordatorios de Flujos de Trabajo
    TASK_DUE_SOON,
    DOC_SIGNED,

    // Seguridad y Auditoría
    SECURITY_SUSPICIOUS_LOGIN,
    SECURITY_ROLE_CHANGED,

    // Sistema y Procesamiento
    SYSTEM_OCR_ERROR,
    SYSTEM_FILE_CONVERSION_FAILED,

    // Colaboración
    COLLABORATION_COMMENT
}
