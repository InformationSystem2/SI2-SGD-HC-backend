package com.sgd_hc.patients.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sgd_hc.patients.entity.Patient;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByDocumentNumber(String documentNumber);
}
