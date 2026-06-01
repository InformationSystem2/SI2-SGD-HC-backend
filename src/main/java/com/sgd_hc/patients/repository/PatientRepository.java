package com.sgd_hc.patients.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.tenants.entity.Tenant;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByDocumentNumber(String documentNumber);
    long countByTenant(Tenant tenant);

    @Modifying
    @Query(value = "DELETE FROM patients WHERE tenant_id = :tenantId", nativeQuery = true)
    void deleteAllByTenantId(@Param("tenantId") UUID tenantId);    
}
