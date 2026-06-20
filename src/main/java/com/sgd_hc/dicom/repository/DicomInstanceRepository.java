package com.sgd_hc.dicom.repository;

import com.sgd_hc.dicom.entity.DicomInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DicomInstanceRepository extends JpaRepository<DicomInstance, UUID> {

    Optional<DicomInstance> findBySopInstanceUid(String sopInstanceUid);

    List<DicomInstance> findBySeriesIdOrderByInstanceNumberAsc(UUID seriesId);

    @Query(value = "SELECT COALESCE(SUM(file_size_bytes), 0) FROM dicom_instances WHERE tenant_id = :tenantId", nativeQuery = true)
    long sumFileSizeBytesByTenantId(@Param("tenantId") UUID tenantId);
}
