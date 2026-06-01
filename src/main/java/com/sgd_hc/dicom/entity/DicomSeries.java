package com.sgd_hc.dicom.entity;

import com.sgd_hc.users.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dicom_series")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DicomSeries extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_series_study"))
    private DicomStudy study;

    @Column(name = "series_instance_uid", nullable = false, unique = true, length = 100)
    private String seriesInstanceUid;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false, length = 20)
    private Modality modality;

    @Column(name = "series_number")
    private Integer seriesNumber;

    @Column(name = "series_description", length = 255)
    private String seriesDescription;

    @Column(name = "body_part", length = 100)
    private String bodyPart;

    @OneToMany(mappedBy = "series", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DicomInstance> instances = new ArrayList<>();
}
