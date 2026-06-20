package com.sgd_hc.tenants.config;

import com.sgd_hc.tenants.entity.SubscriptionStatus;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.tenants.service.PlanService;
import com.sgd_hc.tenants.service.TenantRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpirationJob {

    private final TenantRepository tenantRepository;
    private final TenantRevocationService revocationService;
    private final PlanService planService;

    @Scheduled(cron = "0 0 2 * * *")
    public void checkExpiredSubscriptions() {
        log.info("Iniciando verificación de suscripciones expiradas...");

        LocalDate today = LocalDate.now();

        List<Tenant> activeTenants = tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.ACTIVE);
        List<Tenant> pastDueTenants = tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.PAST_DUE);

        int suspendedCount = processTenants(activeTenants, today, false);
        int pastDueCount = processTenants(pastDueTenants, today, true);

        log.info("Verificación completada: {} suspendidos, {} marcados como PAST_DUE", suspendedCount, pastDueCount);
    }

    private int processTenants(List<Tenant> tenants, LocalDate today, boolean isPastDueContext) {
        int count = 0;
        for (Tenant tenant : tenants) {
            if (tenant.isSuspended()) continue;

            LocalDate endDate = tenant.getSubscriptionEndDate();
            if (endDate == null) continue;

            int graceDays = getGracePeriodDays(tenant);

            if (endDate.isBefore(today)) {
                if (endDate.isBefore(today.minusDays(graceDays))) {
                    suspendTenant(tenant);
                    count++;
                } else if (!isPastDueContext) {
                    markPastDue(tenant);
                    count++;
                }
            }
        }
        return count;
    }

    private int getGracePeriodDays(Tenant tenant) {
        String planName = tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan().name() : "BASIC";
        return planService.getGracePeriodDaysOrDefault(planName, PlanService.DEFAULT_GRACE_PERIOD_DAYS);
    }

    private void suspendTenant(Tenant tenant) {
        tenant.setSubscriptionStatus(SubscriptionStatus.SUSPENDED);
        tenantRepository.save(tenant);

        revocationService.revokeAllTokensForTenant(tenant.getId(), Instant.now());

        log.warn("Tenant {} ({}) suspendido automáticamente por expiración de suscripción",
                tenant.getName(), tenant.getSlug());
    }

    private void markPastDue(Tenant tenant) {
        if (tenant.getSubscriptionStatus() != SubscriptionStatus.PAST_DUE) {
            tenant.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
            tenantRepository.save(tenant);

            log.warn("Tenant {} ({}) marcado como PAST_DUE (período de gracia)",
                    tenant.getName(), tenant.getSlug());
        }
    }
}
