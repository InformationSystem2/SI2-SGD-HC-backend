package com.sgd_hc.patients.repository;

import com.sgd_hc.patients.entity.ClinicalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicalHistoryRepository extends JpaRepository<ClinicalHistory, UUID> {
    Optional<ClinicalHistory> findByPatientId(UUID patientId);
    Optional<ClinicalHistory> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByPatientId(UUID patientId);
}
