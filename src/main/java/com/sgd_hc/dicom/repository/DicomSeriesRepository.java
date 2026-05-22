package com.sgd_hc.dicom.repository;

import com.sgd_hc.dicom.entity.DicomSeries;
import com.sgd_hc.dicom.entity.DicomStudy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DicomSeriesRepository extends JpaRepository<DicomSeries, UUID> {

    Optional<DicomSeries> findBySeriesInstanceUid(String seriesInstanceUid);

    @Query("SELECT sr FROM DicomSeries sr LEFT JOIN FETCH sr.instances WHERE sr.study = :study")
    List<DicomSeries> findByStudyWithInstances(@Param("study") DicomStudy study);
}
