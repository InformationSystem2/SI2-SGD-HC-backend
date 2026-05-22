package com.sgd_hc.documents.controller;

import com.sgd_hc.documents.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class UploadsController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getFile(@PathVariable String filename, HttpServletRequest request) {
        try {
            Resource resource = fileStorageService.loadAsResource(filename);
            
            String contentType = null;
            try {
                contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            } catch (Exception ex) {
                log.debug("No se pudo determinar el tipo de archivo de manera automática mediante getServletContext().");
            }

            if (contentType == null) {
                contentType = "application/octet-stream";
                String nameLower = filename.toLowerCase();
                if (nameLower.endsWith(".pdf")) {
                    contentType = "application/pdf";
                } else if (nameLower.endsWith(".docx")) {
                    contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                } else if (nameLower.endsWith(".doc")) {
                    contentType = "application/msword";
                } else if (nameLower.endsWith(".png")) {
                    contentType = "image/png";
                } else if (nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (nameLower.endsWith(".xlsx")) {
                    contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                } else if (nameLower.endsWith(".xls")) {
                    contentType = "application/vnd.ms-excel";
                } else if (nameLower.endsWith(".dcm")) {
                    contentType = "application/dicom";
                }
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (IOException e) {
            log.error(">>> UploadsController: Error al cargar el archivo '{}': {}", filename, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
