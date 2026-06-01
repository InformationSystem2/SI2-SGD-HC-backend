package com.sgd_hc.dicom.entity;

import com.sgd_hc.users.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "dicom_studies")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DicomStudy extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "uploader_id", nullable = false)
    private UUID uploaderId;

    @Column(name = "study_instance_uid", nullable = false, unique = true, length = 100)
    private String studyInstanceUid;

    @Column(name = "study_date")
    private LocalDate studyDate;

    @Column(name = "study_description", length = 255)
    private String studyDescription;

    @Column(name = "accession_number", length = 50)
    private String accessionNumber;

    @OneToMany(mappedBy = "study", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DicomSeries> series = new ArrayList<>();
}
