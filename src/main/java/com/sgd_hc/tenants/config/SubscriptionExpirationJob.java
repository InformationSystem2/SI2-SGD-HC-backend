package com.sgd_hc.tenants.config;

import com.sgd_hc.tenants.entity.SubscriptionStatus;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.tenants.service.TenantRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Job programado que verifica diariamente las suscripciones vencidas.
 * Ejecuta a las 2:00 AM UTC.
 *
 * Lógica:
 * - subscriptionEndDate se calcula como subscriptionStartDate + 30 días.
 * - Si endDate < hoy y fuera del grace period (3 días): SUSPEND + revoca tokens.
 * - Si endDate < hoy pero dentro del grace period: PAST_DUE.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpirationJob {

    private static final int CYCLE_DAYS = 30;
    private static final int GRACE_PERIOD_DAYS = 3;

    private final TenantRepository tenantRepository;
    private final TenantRevocationService revocationService;

    /**
     * Verifica suscripciones expiradas todos los días a las 2:00 AM UTC.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void checkExpiredSubscriptions() {
        log.info("Iniciando verificación de suscripciones expiradas...");

        LocalDate today = LocalDate.now();
        LocalDate graceThreshold = today.minusDays(GRACE_PERIOD_DAYS);

        List<Tenant> activeTenants = tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.ACTIVE);
        List<Tenant> pastDueTenants = tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.PAST_DUE);

        int suspendedCount = processTenants(activeTenants, today, graceThreshold, false);
        int pastDueCount = processTenants(pastDueTenants, today, graceThreshold, true);

        log.info("Verificación completada: {} suspendidos, {} marcados como PAST_DUE", suspendedCount, pastDueCount);
    }

    private int processTenants(List<Tenant> tenants, LocalDate today, LocalDate graceThreshold, boolean isPastDueContext) {
        int count = 0;
        for (Tenant tenant : tenants) {
            if (tenant.isSuspended()) continue;

            LocalDate endDate = calculateEndDate(tenant);

            if (endDate.isBefore(today)) {
                if (endDate.isBefore(graceThreshold)) {
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

    private LocalDate calculateEndDate(Tenant tenant) {
        return tenant.getSubscriptionStartDate().plusDays(CYCLE_DAYS);
    }
}
