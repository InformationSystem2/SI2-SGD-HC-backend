package com.sgd_hc.dicom.service;

import com.sgd_hc.dicom.dto.DicomUploadMultiItemDto;
import com.sgd_hc.dicom.dto.DicomUploadMultiResultDto;
import com.sgd_hc.dicom.entity.DicomInstance;
import com.sgd_hc.dicom.entity.DicomSeries;
import com.sgd_hc.dicom.entity.DicomStudy;
import com.sgd_hc.dicom.entity.Modality;
import com.sgd_hc.dicom.mapper.DicomMapper;
import com.sgd_hc.dicom.repository.DicomInstanceRepository;
import com.sgd_hc.dicom.repository.DicomSeriesRepository;
import com.sgd_hc.dicom.repository.DicomStudyRepository;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.patients.repository.PatientRepository;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sgd_hc.documents.service.FileStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DicomParserService {

    private static final DateTimeFormatter DICOM_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final DicomStudyRepository    studyRepository;
    private final DicomSeriesRepository   seriesRepository;
    private final DicomInstanceRepository instanceRepository;
    private final PatientRepository       patientRepository;
    private final TenantResolverService   tenantResolverService;
    private final DicomMapper             dicomMapper;
    private final FileStorageService      fileStorageService;

    /**
     * Punto de entrada principal. Persiste la jerarquía Study → Series → Instance
     * a partir de un único archivo .dcm.
     */
    @Transactional
    public DicomStudy parseAndPersist(MultipartFile file, UUID patientId) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo DICOM no puede estar vacío.");
        }

        Tenant tenant   = tenantResolverService.resolve();
        User uploader   = currentUser();
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Paciente no encontrado con id: " + patientId));

        Attributes attrs = readAttributes(file);

        String studyUid  = requireTag(attrs, Tag.StudyInstanceUID,  "StudyInstanceUID");
        String seriesUid = requireTag(attrs, Tag.SeriesInstanceUID, "SeriesInstanceUID");
        String sopUid    = requireTag(attrs, Tag.SOPInstanceUID,    "SOPInstanceUID");

        if (instanceRepository.findBySopInstanceUid(sopUid).isPresent()) {
            log.info("Instancia DICOM ya existe, omitiendo: {}", sopUid);
            return studyRepository.findByStudyInstanceUid(studyUid)
                    .orElseThrow(() -> new IllegalStateException("Instancia huérfana: " + sopUid));
        }

        String filePath = fileStorageService.storeDicom(file, studyUid, seriesUid, sopUid);

        DicomStudy  study  = findOrCreateStudy(attrs, studyUid, patient, tenant, uploader);
        DicomSeries series = findOrCreateSeries(attrs, seriesUid, study, tenant);
        createInstance(attrs, sopUid, series, filePath, tenant);

        log.info("DICOM ingestado — Study: {} | Series: {} | SOP: {}", studyUid, seriesUid, sopUid);
        return study;
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
    @Transactional
    public DicomUploadMultiResultDto parseAndPersistMulti(List<MultipartFile> files, UUID patientId) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Debe enviarse al menos un archivo .dcm.");
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Paciente no encontrado con id: " + patientId));
        Tenant tenant   = tenantResolverService.resolve();
        User uploader   = currentUser();

        List<DicomUploadMultiItemDto> uploaded = new ArrayList<>();
        List<DicomUploadMultiItemDto> skipped  = new ArrayList<>();
        List<DicomUploadMultiItemDto> errors   = new ArrayList<>();

        // Estudio resultante: el primero que reciba al menos una instancia nueva.
        UUID firstNewStudyId = null;

        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "(sin nombre)";

            if (file.isEmpty()) {
                errors.add(new DicomUploadMultiItemDto(filename, null, "empty-file"));
                continue;
            }

            // 1. Parsear header
            Attributes attrs;
            try {
                attrs = readAttributes(file);
            } catch (Exception e) {
                log.warn("Archivo no DICOM o malformado: {} ({})", filename, e.getMessage());
                errors.add(new DicomUploadMultiItemDto(filename, null, "invalid-dicom"));
                continue;
            }

            // 2. Tags obligatorios
            String studyUid, seriesUid, sopUid;
            try {
                studyUid  = requireTag(attrs, Tag.StudyInstanceUID,  "StudyInstanceUID");
                seriesUid = requireTag(attrs, Tag.SeriesInstanceUID, "SeriesInstanceUID");
                sopUid    = requireTag(attrs, Tag.SOPInstanceUID,    "SOPInstanceUID");
            } catch (IllegalArgumentException e) {
                errors.add(new DicomUploadMultiItemDto(filename, null, "missing-tag"));
                continue;
            }

            // 3. Duplicado por SOPInstanceUID
            if (instanceRepository.findBySopInstanceUid(sopUid).isPresent()) {
                skipped.add(new DicomUploadMultiItemDto(filename, sopUid, "duplicate"));
                continue;
            }

            // 4. Persistir (Study → Series → Instance)
            try {
                DicomStudy study = findOrCreateStudy(attrs, studyUid, patient, tenant, uploader);
                DicomSeries series = findOrCreateSeries(attrs, seriesUid, study, tenant);
                String filePath = fileStorageService.storeDicom(file, studyUid, seriesUid, sopUid);
                createInstance(attrs, sopUid, series, filePath, tenant);

                if (firstNewStudyId == null) firstNewStudyId = study.getId();
                uploaded.add(new DicomUploadMultiItemDto(filename, sopUid, null));
                log.info("DICOM ingestado (batch) — Study: {} | SOP: {} | file: {}",
                        studyUid, sopUid, filename);
            } catch (IOException e) {
                log.error("Error al guardar {}: {}", filename, e.getMessage());
                errors.add(new DicomUploadMultiItemDto(filename, sopUid, "io-error"));
            } catch (Exception e) {
                log.error("Error persistiendo {}: {}", filename, e.getMessage(), e);
                errors.add(new DicomUploadMultiItemDto(filename, sopUid, "persist-error"));
            }
        }

        var studyDto = (firstNewStudyId != null)
                ? dicomMapper.toStudyDto(getStudyWithTree(firstNewStudyId))
                : null;

        return new DicomUploadMultiResultDto(studyDto, uploaded, skipped, errors);
    }

    // ── Lectura de metadatos ──────────────────────────────────────────────────

    private Attributes readAttributes(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             DicomInputStream dis = new DicomInputStream(is)) {
            // saltar pixel data (7FE0,0010) para parseo rápido de metadatos
            dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
            dis.readFileMetaInformation();
            return dis.readDataset();
        }
    }

    // ── Find-or-create: Study ─────────────────────────────────────────────────

    private DicomStudy findOrCreateStudy(Attributes attrs, String studyUid,
                                         Patient patient, Tenant tenant,
                                         User uploader) {
        return studyRepository.findByStudyInstanceUid(studyUid).orElseGet(() -> {
            DicomStudy study = new DicomStudy();
            study.setTenant(tenant);
            study.setPatientId(patient.getId());
            study.setUploaderId(uploader.getId());
            study.setStudyInstanceUid(studyUid);
            study.setStudyDate(parseDicomDate(attrs.getString(Tag.StudyDate)));
            study.setStudyDescription(attrs.getString(Tag.StudyDescription));
            study.setAccessionNumber(attrs.getString(Tag.AccessionNumber));
            return studyRepository.save(study);
        });
    }

    private User currentUser() {
        Object principal = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        if (principal instanceof SecurityUser su) return su.getUser();
        throw new IllegalStateException("No se pudo determinar el usuario autenticado");
    }

    // ── Find-or-create: Series ────────────────────────────────────────────────

    private DicomSeries findOrCreateSeries(Attributes attrs, String seriesUid,
                                            DicomStudy study, Tenant tenant) {
        return seriesRepository.findBySeriesInstanceUid(seriesUid).orElseGet(() -> {
            DicomSeries series = new DicomSeries();
            series.setTenant(tenant);
            series.setStudy(study);
            series.setSeriesInstanceUid(seriesUid);
            series.setModality(parseModality(attrs.getString(Tag.Modality)));
            series.setSeriesNumber(attrs.getInt(Tag.SeriesNumber, 0));
            series.setSeriesDescription(attrs.getString(Tag.SeriesDescription));
            series.setBodyPart(attrs.getString(Tag.BodyPartExamined));
            return seriesRepository.save(series);
        });
    }

    // ── Crear instancia ───────────────────────────────────────────────────────

    private void createInstance(Attributes attrs, String sopUid,
                                DicomSeries series, String filePath,
                                Tenant tenant) {
        Double[] pixelSpacing = extractPixelSpacing(attrs);

        double[] wc = attrs.getDoubles(Tag.WindowCenter);
        double[] ww = attrs.getDoubles(Tag.WindowWidth);

        DicomInstance instance = new DicomInstance();
        instance.setTenant(tenant);
        instance.setSeries(series);
        instance.setSopInstanceUid(sopUid);
        instance.setInstanceNumber(attrs.getInt(Tag.InstanceNumber, 0));
        instance.setFilePath(filePath);
        instance.setRows(attrs.getInt(Tag.Rows, 0));
        instance.setColumns(attrs.getInt(Tag.Columns, 0));
        instance.setBitsAllocated(attrs.getInt(Tag.BitsAllocated, 0));
        instance.setWindowCenter((wc != null && wc.length > 0) ? wc[0] : null);
        instance.setWindowWidth((ww != null && ww.length > 0) ? ww[0] : null);
        instance.setPixelSpacing(pixelSpacing);

        instanceRepository.save(instance);
    }

    // —— Helpers de conversión ——————————————————————————————————————————

    private LocalDate parseDicomDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr, DICOM_DATE);
        } catch (DateTimeParseException e) {
            log.warn("Fecha DICOM inválida ignorada: '{}'", dateStr);
            return null;
        }
    }

    private Modality parseModality(String value) {
        if (value == null) return Modality.OT;
        try {
            return Modality.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Modalidad desconocida '{}', asignando OT", value);
            return Modality.OT;
        }
    }

    private Double[] extractPixelSpacing(Attributes attrs) {
        double[] ps = attrs.getDoubles(Tag.PixelSpacing);
        if (ps == null || ps.length < 2) return null;
        return new Double[]{ps[0], ps[1]};
    }

    private String requireTag(Attributes attrs, int tag, String name) {
        String value = attrs.getString(tag);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tag DICOM obligatorio ausente: " + name);
        }
        return value.trim();
    }

    // —— Consultas públicas ———————————————————————————————————————————

    @Transactional(readOnly = true)
    public DicomStudy getStudyWithTree(UUID studyId) {
        DicomStudy study = studyRepository.findByIdWithSeries(studyId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Estudio DICOM no encontrado: " + studyId));
        seriesRepository.findByStudyWithInstances(study);
        return study;
    }

    @Transactional(readOnly = true)
    public List<DicomStudy> listAllStudies() {
        List<DicomStudy> studies = studyRepository.findAllWithSeries();
        studies.forEach(seriesRepository::findByStudyWithInstances);
        return studies;
    }

    @Transactional(readOnly = true)
    public List<DicomStudy> listStudiesByPatient(UUID patientId) {
        List<DicomStudy> studies = studyRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        studies.forEach(s -> seriesRepository.findByStudyWithInstances(s));
        return studies;
    }

    @Transactional(readOnly = true)
    public String getFilePath(UUID instanceId) {
        return instanceRepository.findById(instanceId)
                .map(DicomInstance::getFilePath)
                .orElseThrow(() -> new NoSuchElementException(
                        "Instancia DICOM no encontrada: " + instanceId));
    }
}
