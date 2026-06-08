package com.sgd_hc.backups.mapper;

import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;

@Component
public class BackupMapper {

    public Map<String, Object> toAuditMap(File entity) {
        if (entity == null) return Map.of();
        return Map.of(
            "fileName", entity.getName(),
            "absolutePath", entity.getAbsolutePath(),
            "sizeBytes", entity.length()
        );
    }
}
