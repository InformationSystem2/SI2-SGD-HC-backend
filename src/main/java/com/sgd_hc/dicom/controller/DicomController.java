package com.sgd_hc.dicom.controller;

import com.sgd_hc.dicom.dto.DicomStudyDto;
import com.sgd_hc.dicom.dto.DicomUploadMultiResultDto;
import com.sgd_hc.dicom.entity.DicomStudy;
import com.sgd_hc.dicom.mapper.DicomMapper;
import com.sgd_hc.dicom.service.DicomParserService;
import com.sgd_hc.documents.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/dicom")
@RequiredArgsConstructor
public class DicomController {

    private final DicomParserService parserService;
    private final DicomMapper        mapper;
    private final FileStorageService  fileStorageService;

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Recibe un archivo .dcm, extrae la jerarquía DICOM y la persiste.
     * El parámetro {@code patientId} vincula el estudio al paciente.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DICOM_CREATE')")
    public ResponseEntity<DicomStudyDto> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("patientId") UUID patientId) throws IOException {

        DicomStudy study = parserService.parseAndPersist(file, patientId);
        // Recargamos con el árbol completo para devolverlo en la respuesta
        DicomStudy withTree = parserService.getStudyWithTree(study.getId());
        return new ResponseEntity<>(mapper.toStudyDto(withTree), HttpStatus.CREATED);
    }

    /**
     * Carga en lote: recibe varios archivos DICOM y los asocia directamente al
     * paciente. Rechaza duplicados por SOPInstanceUID sin abortar el resto.
     *
     * <p>Respuesta: {@link DicomUploadMultiResultDto} con conteos
     * {@code uploaded} / {@code skipped} / {@code errors} y el primer estudio
     * con instancias nuevas para que el cliente abra el visor.
     *
     * <p>HTTP 200 incluso si todo se omitió — no es un error de cliente.
     */
    @PostMapping(value = "/upload-multi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DICOM_CREATE')")
    public ResponseEntity<DicomUploadMultiResultDto> uploadMulti(
            @RequestParam("patientId") UUID patientId,
            @RequestPart("files") List<MultipartFile> files) {

        DicomUploadMultiResultDto result = parserService.parseAndPersistMulti(files, patientId);
        return ResponseEntity.ok(result);
    }

    // ── Listado de todos los estudios ─────────────────────────────────────────

    @GetMapping("/studies")
    @PreAuthorize("hasAuthority('DICOM_READ')")
    public ResponseEntity<List<DicomStudyDto>> listStudies() {
        List<DicomStudyDto> studies = parserService.listAllStudies().stream()
                .map(mapper::toStudyDto)
                .toList();
        return ResponseEntity.ok(studies);
    }

    // ── Consultas del árbol jerárquico ────────────────────────────────────────

    /**
     * Devuelve el árbol completo Study → Series[] → Instance[] por ID de estudio.
     */
    @GetMapping("/studies/{id}")
    @PreAuthorize("hasAuthority('DICOM_READ')")
    public ResponseEntity<DicomStudyDto> getStudy(@PathVariable UUID id) {
        DicomStudy study = parserService.getStudyWithTree(id);
        return ResponseEntity.ok(mapper.toStudyDto(study));
    }

    /**
     * Devuelve todos los estudios DICOM asociados a un paciente.
     * Útil para listar el historial de imágenes del paciente.
     */
    @GetMapping("/studies/patient/{patientId}")
    @PreAuthorize("hasAuthority('DICOM_READ')")
    public ResponseEntity<List<DicomStudyDto>> getStudiesByPatient(@PathVariable UUID patientId) {
        List<DicomStudyDto> studies = parserService.listStudiesByPatient(patientId).stream()
                .map(mapper::toStudyDto)
                .toList();
        return ResponseEntity.ok(studies);
    }

    // ── Descarga del archivo físico .dcm ─────────────────────────────────────

    /**
     * Sirve el archivo .dcm físico para que Cornerstone3D pueda cargarlo
     * mediante el esquema {@code wadouri:http://host/api/dicom/instances/{id}/file}.
     *
     * <p>El Content-Type {@code application/dicom} es el tipo MIME estándar DICOM
     * (RFC 3240). Se añade {@code Accept-Ranges} para soportar peticiones parciales
     * que algunos loaders usan para leer solo la cabecera.
     */
    @GetMapping("/instances/{id}/file")
    @PreAuthorize("hasAuthority('DICOM_READ')")
    public ResponseEntity<Resource> getInstanceFile(@PathVariable UUID id) {
        String filePath = parserService.getFilePath(id);

        Resource resource;
        try {
            resource = fileStorageService.loadAsResource(filePath);
        } catch (IOException e) {
            throw new NoSuchElementException("Archivo DICOM no disponible para la instancia: " + id);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/dicom"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + id + ".dcm\"")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource);
    }
}
