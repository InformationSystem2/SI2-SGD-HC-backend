package com.sgd_hc.tenants.dto;

public record TenantStatsDto(
        int userCount,
        int maxUsers,
        long storageUsedMB,
        long maxStorageMB,
        long apiCallsUsed,
        long maxApiCalls,
        long patientCount,
        long maxPatients,
        long documentCount,
        long maxDocuments,
        long dicomStudyCount,
        long maxDicomStudies,
        long roleCount,
        long maxStaffRoles
) {}