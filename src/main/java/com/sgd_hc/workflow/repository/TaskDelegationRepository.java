package com.sgd_hc.workflow.repository;

import com.sgd_hc.workflow.entity.TaskDelegation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskDelegationRepository extends JpaRepository<TaskDelegation, UUID> {

    Optional<TaskDelegation> findByIdAndTenantId(UUID id, UUID tenantId);

    List<TaskDelegation> findByDelegatorIdAndTenantIdAndIsActiveTrue(UUID delegatorId, UUID tenantId);

    Optional<TaskDelegation> findFirstByDelegatorIdAndTenantIdAndIsActiveTrueAndStartDateLessThanEqualAndEndDateIsNull(
            UUID delegatorId, UUID tenantId, LocalDate today);

    Optional<TaskDelegation> findFirstByDelegatorIdAndTenantIdAndIsActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID delegatorId, UUID tenantId, LocalDate today, LocalDate today2);
}
