package com.sgd_hc.tenants.repository;

import com.sgd_hc.tenants.entity.PlanLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanLimitRepository extends JpaRepository<PlanLimit, UUID> {
    List<PlanLimit> findAllByPlanId(UUID planId);
}
