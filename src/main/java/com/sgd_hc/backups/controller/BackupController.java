package com.sgd_hc.backups.controller;

import com.sgd_hc.backups.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SUPERUSER')")
public class BackupController {

    private final BackupService backupService;

    @GetMapping("/generate/full")
    public ResponseEntity<Resource> generateFullBackup() {
        File backupFile = backupService.generateFullBackup();
        Resource resource = new FileSystemResource(backupFile);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + backupFile.getName() + "\"")
                .contentLength(backupFile.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/generate/tenant/{slug}")
    public ResponseEntity<Resource> generateTenantBackup(@PathVariable String slug) {
        File backupFile = backupService.generateTenantBackup(slug);
        Resource resource = new FileSystemResource(backupFile);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + backupFile.getName() + "\"")
                .contentLength(backupFile.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/restore/full")
    public ResponseEntity<Map<String, String>> restoreFullBackup(@RequestParam("file") MultipartFile file) throws IOException {
        backupService.restoreFullBackup(file);
        return ResponseEntity.ok(Map.of("message", "Restauración completa finalizada exitosamente"));
    }

    @PostMapping("/restore/tenant")
    public ResponseEntity<Map<String, String>> restoreTenantBackup(@RequestParam("file") MultipartFile file) throws IOException {
        backupService.restoreTenantBackup(file);
        return ResponseEntity.ok(Map.of("message", "Restauración del tenant finalizada exitosamente"));
    }
}
