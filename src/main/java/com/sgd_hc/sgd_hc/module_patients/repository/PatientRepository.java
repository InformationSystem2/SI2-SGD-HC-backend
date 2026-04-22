package com.sgd_hc.sgd_hc.module_patients.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sgd_hc.sgd_hc.module_patients.entity.Patient;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    Optional<Patient> findByDocumentNumber(String documentNumber);
}
