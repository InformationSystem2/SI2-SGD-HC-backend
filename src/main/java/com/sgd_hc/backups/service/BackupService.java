package com.sgd_hc.backups.service;

import com.sgd_hc.backups.entity.BackupHistory;
import com.sgd_hc.backups.repository.BackupHistoryRepository;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.tenants.service.PlanLimitValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.service.AuditableService;
import com.sgd_hc.backups.mapper.BackupMapper;
import lombok.RequiredArgsConstructor;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService implements AuditableService<String, File> {

    private final BackupMapper backupMapper;
    private final PlanLimitValidator planLimitValidator;
    private final BackupHistoryRepository backupHistoryRepository;
    private final TenantRepository tenantRepository;

    private final boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
    private final String BACKUP_DIR = "true".equals(System.getenv("RUNNING_IN_DOCKER")) ? "/backups" : "backups";

    @Auditable(resourceType = "BACKUP_FULL", actionType = ActionType.CREATE)
    public File generateFullBackup() {
        log.info("Iniciando backup completo manual");
        List<String> command = isWindows 
                ? Arrays.asList("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", "scripts/backup/windows/backup.ps1", "-FullOnly")
                : Arrays.asList("bash", "scripts/backup/linux/backup.sh", "--full-only");
        
        executeCommand(command);
        
        return getLatestFile(BACKUP_DIR, "backup_completo_");
    }

    @Auditable(resourceType = "BACKUP_TENANT", actionType = ActionType.CREATE, idParamName = "tenantSlug")
    public File generateTenantBackup(String tenantSlug) {
        log.info("Iniciando backup manual para el tenant: {}", tenantSlug);

        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + tenantSlug));
        planLimitValidator.checkBackupsLimit(tenant.getId());

        List<String> command = isWindows
                ? Arrays.asList("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", "scripts/backup/windows/backup.ps1", "-TenantSlug", tenantSlug)
                : Arrays.asList("bash", "scripts/backup/linux/backup.sh", "--tenant-slug", tenantSlug);

        executeCommand(command);

        BackupHistory record = new BackupHistory();
        record.setId(UUID.randomUUID());
        record.setTenant(tenant);
        record.setBackupType("tenant");
        backupHistoryRepository.save(record);

        return getLatestFile(BACKUP_DIR, "backup_tenant_" + tenantSlug);
    }

    @Auditable(resourceType = "BACKUP_RESTORE_FULL", actionType = ActionType.UPDATE)
    public void restoreFullBackup(MultipartFile file) throws IOException {
        log.info("Iniciando restauración completa manual");
        File tempFile = saveTempFile(file, ".dump");
        try {
            List<String> command = isWindows
                    ? Arrays.asList("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", "scripts/backup/windows/restore.ps1", "-Mode", "-full", "-BackupFile", tempFile.getAbsolutePath(), "-Force")
                    : Arrays.asList("bash", "scripts/backup/linux/restore.sh", "--full", tempFile.getAbsolutePath(), "--force");
            executeCommand(command);
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    @Auditable(resourceType = "BACKUP_RESTORE_TENANT", actionType = ActionType.UPDATE)
    public void restoreTenantBackup(MultipartFile file) throws IOException {
        log.info("Iniciando restauración manual de tenant");
        File tempFile = saveTempFile(file, ".sql");
        try {
            List<String> command = isWindows
                    ? Arrays.asList("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", "scripts/backup/windows/restore.ps1", "-Mode", "-tenant", "-BackupFile", tempFile.getAbsolutePath(), "-Force")
                    : Arrays.asList("bash", "scripts/backup/linux/restore.sh", "--tenant", tempFile.getAbsolutePath(), "--force");
            executeCommand(command);
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    private void executeCommand(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (Scanner s = new Scanner(process.getInputStream()).useDelimiter("\\A")) {
                output = s.hasNext() ? s.next() : "";
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Error ejecutando script de backup/restore. Salida: {}", output);
                throw new RuntimeException("El proceso finalizó con código " + exitCode + ": " + output);
            }
            log.info("Script ejecutado correctamente. Salida: {}", output);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fallo al ejecutar el proceso", e);
            throw new RuntimeException("No se pudo ejecutar la operación de backup/restore", e);
        }
    }

    private File getLatestFile(String dirPath, String prefix) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new RuntimeException("Directorio de backups no encontrado");
        }
        
        File[] files = dir.listFiles((d, name) -> name.startsWith(prefix));
        if (files == null || files.length == 0) {
            throw new RuntimeException("No se encontró el archivo de backup generado");
        }
        
        return Arrays.stream(files)
                .max(Comparator.comparingLong(File::lastModified))
                .orElseThrow(() -> new RuntimeException("No se pudo determinar el archivo más reciente"));
    }

    private File saveTempFile(MultipartFile file, String extension) throws IOException {
        Path tempPath = Files.createTempFile("restore_", extension);
        Files.copy(file.getInputStream(), tempPath, StandardCopyOption.REPLACE_EXISTING);
        return tempPath.toFile();
    }

    @Override
    public File getEntity(String id) {
        return null;
    }

    @Override
    public Map<String, Object> toAuditMap(File entity) {
        return backupMapper.toAuditMap(entity);
    }

    @Override
    public Map<String, Object> toAuditMapFromResult(Object result) {
        if (result instanceof File file) {
            return toAuditMap(file);
        }
        return Map.of();
    }
}
