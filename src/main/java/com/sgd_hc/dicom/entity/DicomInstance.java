package com.sgd_hc.dicom.entity;

import com.sgd_hc.users.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dicom_instances")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DicomInstance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_instance_series"))
    private DicomSeries series;

    @Column(name = "sop_instance_uid", nullable = false, unique = true, length = 100)
    private String sopInstanceUid;

    @Column(name = "instance_number")
    private Integer instanceNumber;

    @Column(name = "file_path", nullable = false, columnDefinition = "text")
    private String filePath;

    @Column(name = "rows")
    private Integer rows;

    @Column(name = "columns")
    private Integer columns;

    @Column(name = "bits_allocated")
    private Integer bitsAllocated;

    @Column(name = "window_center")
    private Double windowCenter;

    @Column(name = "window_width")
    private Double windowWidth;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "pixel_spacing", columnDefinition = "numeric[]")
    private Double[] pixelSpacing;
}
