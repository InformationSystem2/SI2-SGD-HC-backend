package com.sgd_hc.workflow.service;

import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.repository.UserRepository;
import com.sgd_hc.workflow.entity.TaskDelegation;
import com.sgd_hc.workflow.repository.TaskDelegationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DelegationService {

    private final TaskDelegationRepository delegationRepository;
    private final TenantResolverService tenantResolverService;
    private final UserRepository userRepository;

    @Transactional
    public TaskDelegation createDelegation(UUID delegateId, LocalDate startDate, LocalDate endDate) {
        UUID tenantId = tenantResolverService.resolve().getId();
        User delegator = currentUser();

        if (delegator.getId().equals(delegateId)) {
            throw new IllegalStateException("No puedes delegar tareas a ti mismo");
        }

        User delegate = userRepository.findById(delegateId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + delegateId));

        TaskDelegation delegation = TaskDelegation.builder()
                .tenantId(tenantId)
                .delegator(delegator)
                .delegate(delegate)
                .startDate(startDate)
                .endDate(endDate)
                .isActive(true)
                .build();

        TaskDelegation saved = delegationRepository.save(delegation);
        log.info("Delegación creada: delegator={}, delegate={}, start={}, end={}",
                delegator.getId(), delegateId, startDate, endDate);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TaskDelegation> getActiveDelegations() {
        UUID tenantId = tenantResolverService.resolve().getId();
        User delegator = currentUser();
        return delegationRepository.findByDelegatorIdAndTenantIdAndIsActiveTrue(delegator.getId(), tenantId);
    }

    @Transactional
    public void cancelDelegation(UUID delegationId) {
        UUID tenantId = tenantResolverService.resolve().getId();
        TaskDelegation delegation = delegationRepository.findByIdAndTenantId(delegationId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Delegación no encontrada: " + delegationId));

        delegation.setIsActive(false);
        delegationRepository.save(delegation);
        log.info("Delegación cancelada: delegationId={}", delegationId);
    }

    @Transactional(readOnly = true)
    public UUID resolveAssignee(UUID originalAssigneeId) {
        UUID tenantId = tenantResolverService.resolve().getId();
        LocalDate today = LocalDate.now();

        // Buscar delegación activa sin fecha fin
        var delegation = delegationRepository
                .findFirstByDelegatorIdAndTenantIdAndIsActiveTrueAndStartDateLessThanEqualAndEndDateIsNull(
                        originalAssigneeId, tenantId, today);

        if (delegation.isPresent()) {
            return delegation.get().getDelegate().getId();
        }

        // Buscar delegación activa con rango de fechas
        delegation = delegationRepository
                .findFirstByDelegatorIdAndTenantIdAndIsActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        originalAssigneeId, tenantId, today, today);

        return delegation.map(d -> d.getDelegate().getId()).orElse(originalAssigneeId);
    }

    private User currentUser() {
        Object principal = Objects.requireNonNull(
                SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        if (principal instanceof SecurityUser su) return su.getUser();
        throw new IllegalStateException("No se pudo determinar el usuario autenticado");
    }
}
