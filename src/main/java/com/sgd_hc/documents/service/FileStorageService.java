package com.sgd_hc.documents.service;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    @Value("${storage.upload-dir:uploads}")
    private String uploadDir;

    @Value("${azure.storage.connection-string:}")
    private String connectionString;

    @Value("${azure.storage.container-name:uploads}")
    private String containerName;

    private BlobContainerClient blobContainerClient;

    @PostConstruct
    public void init() {
        if (StringUtils.hasText(connectionString)) {
            try {
                this.blobContainerClient = new BlobServiceClientBuilder()
                        .connectionString(connectionString)
                        .buildClient()
                        .getBlobContainerClient(containerName);
                
                if (!this.blobContainerClient.exists()) {
                    this.blobContainerClient.create();
                    log.info(">>> Azure Storage: Contenedor '{}' creado con éxito.", containerName);
                } else {
                    log.info(">>> Azure Storage: Conectado al contenedor '{}'.", containerName);
                }
            } catch (Exception e) {
                log.error(">>> Azure Storage: Error al conectar a Azure Blob Storage. Se usará fallback local.", e);
                this.blobContainerClient = null;
            }
        } else {
            log.info(">>> FileStorage: Almacenamiento local activo en el directorio: {}", uploadDir);
        }
    }

    public boolean isAzureActive() {
        return blobContainerClient != null;
    }

    /**
     * Guarda el archivo y retorna la URL relativa pública (/uploads/filename).
     */
    public String store(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo no puede estar vacío");
        }

        String original = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        String extension = original.contains(".")
                ? original.substring(original.lastIndexOf('.'))
                : "";
        String filename = UUID.randomUUID() + extension;

        if (isAzureActive()) {
            try (InputStream is = file.getInputStream()) {
                blobContainerClient.getBlobClient(filename)
                        .upload(is, file.getSize(), true);
                log.info(">>> FileStorage: Archivo guardado en Azure Blob: {}", filename);
            }
        } else {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path dest = dir.resolve(filename);
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            log.info(">>> FileStorage: Archivo guardado localmente en: {}", dest);
        }

        return "/uploads/" + filename;
    }

    /**
     * Guarda un arreglo de bytes y retorna la URL relativa pública (/uploads/filename).
     */
    public String store(String filename, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("El contenido del archivo no puede estar vacío");
        }

        if (isAzureActive()) {
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                blobContainerClient.getBlobClient(filename)
                        .upload(is, bytes.length, true);
                log.info(">>> FileStorage: Archivo de bytes guardado en Azure Blob: {}", filename);
            }
        } else {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path dest = dir.resolve(filename);
            Files.write(dest, bytes);
            log.info(">>> FileStorage: Archivo de bytes guardado localmente en: {}", dest);
        }

        return "/uploads/" + filename;
    }

    /**
     * Guarda un archivo DICOM bajo la estructura jerárquica de UIDs.
     * Retorna la ruta física local (si es local) o el nombre del blob relativo (si es Azure).
     */
    public String storeDicom(MultipartFile file, String studyUid, String seriesUid, String sopUid) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo DICOM no puede estar vacío");
        }

        String relativePath = "dicom/" + studyUid + "/" + seriesUid + "/" + sopUid + ".dcm";

        if (isAzureActive()) {
            try (InputStream is = file.getInputStream()) {
                blobContainerClient.getBlobClient(relativePath)
                        .upload(is, file.getSize(), true);
                log.info(">>> FileStorage: DICOM guardado en Azure Blob: {}", relativePath);
            }
            return relativePath;
        } else {
            Path dir = Paths.get(uploadDir, "dicom", studyUid, seriesUid).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path dest = dir.resolve(sopUid + ".dcm");
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            log.info(">>> FileStorage: DICOM guardado localmente en: {}", dest);
            return dest.toString();
        }
    }

    /**
     * Verifica si un archivo o blob existe.
     */
    public boolean exists(String pathOrFilename) {
        if (isAzureActive()) {
            String blobName = resolveBlobName(pathOrFilename);
            if (blobContainerClient.getBlobClient(blobName).exists()) {
                return true;
            }
        }
        try {
            Path file = Paths.get(uploadDir).resolve(resolveBlobName(pathOrFilename)).normalize();
            if (Files.exists(file)) return true;
        } catch (Exception e) {
            // ignore
        }
        try {
            return Files.exists(Paths.get(pathOrFilename));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Carga un archivo o blob como un recurso de Spring.
     */
    public Resource loadAsResource(String pathOrFilename) throws IOException {
        if (isAzureActive()) {
            String blobName = resolveBlobName(pathOrFilename);
            var blobClient = blobContainerClient.getBlobClient(blobName);
            if (blobClient.exists()) {
                byte[] bytes = blobClient.downloadContent().toBytes();
                return new ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return Paths.get(blobName).getFileName().toString();
                    }
                };
            }
            log.warn(">>> FileStorage: Archivo no encontrado en Azure Blob, intentando buscar localmente: {}", blobName);
        }

        String relativeName = resolveBlobName(pathOrFilename);
        Path file = Paths.get(uploadDir).resolve(relativeName).normalize();
        if (!Files.exists(file)) {
            try {
                Path absPath = Paths.get(pathOrFilename);
                if (Files.exists(absPath)) {
                    file = absPath;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        Resource resource = new UrlResource(file.toUri());
        if (resource.exists() || resource.isReadable()) {
            return resource;
        } else {
            throw new IOException("Archivo no encontrado ni en Azure ni en disco local: " + pathOrFilename);
        }
    }

    /**
     * Resuelve el nombre del blob o ruta relativa a partir de una ruta absoluta o URL de uploads.
     */
    private String resolveBlobName(String pathOrFilename) {
        if (pathOrFilename == null) {
            return null;
        }
        String path = pathOrFilename.replace("\\", "/");
        
        if (path.startsWith("/uploads/")) {
            path = path.substring("/uploads/".length());
        } else if (path.startsWith("uploads/")) {
            path = path.substring("uploads/".length());
        }
        
        String uploadDirNormalized = Paths.get(uploadDir).toAbsolutePath().toString().replace("\\", "/");
        if (path.startsWith(uploadDirNormalized)) {
            path = path.substring(uploadDirNormalized.length());
        }
        
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        return path;
    }
}
