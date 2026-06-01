package com.sgd_hc.dicom.repository;

import com.sgd_hc.dicom.entity.DicomStudy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DicomStudyRepository extends JpaRepository<DicomStudy, UUID> {

    Optional<DicomStudy> findByStudyInstanceUid(String studyInstanceUid);

    List<DicomStudy> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    @Query("SELECT s FROM DicomStudy s LEFT JOIN FETCH s.series WHERE s.id = :id")
    Optional<DicomStudy> findByIdWithSeries(@Param("id") UUID id);

    @Query("SELECT DISTINCT s FROM DicomStudy s LEFT JOIN FETCH s.series ORDER BY s.createdAt DESC")
    List<DicomStudy> findAllWithSeries();
}
