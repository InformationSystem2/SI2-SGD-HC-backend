package com.sgd_hc.dicom.repository;

import com.sgd_hc.dicom.entity.DicomInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DicomInstanceRepository extends JpaRepository<DicomInstance, UUID> {

    Optional<DicomInstance> findBySopInstanceUid(String sopInstanceUid);

    List<DicomInstance> findBySeriesIdOrderByInstanceNumberAsc(UUID seriesId);
}
