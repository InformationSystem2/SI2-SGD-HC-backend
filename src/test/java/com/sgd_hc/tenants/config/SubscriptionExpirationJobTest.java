package com.sgd_hc.tenants.config;

import com.sgd_hc.tenants.entity.SubscriptionPlan;
import com.sgd_hc.tenants.entity.SubscriptionStatus;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.tenants.service.PlanService;
import com.sgd_hc.tenants.service.TenantRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionExpirationJobTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantRevocationService revocationService;
    @Mock private PlanService planService;

    @InjectMocks private SubscriptionExpirationJob job;

    private Tenant createTenant(SubscriptionStatus status, SubscriptionPlan plan, LocalDate endDate) {
        Tenant t = new Tenant();
        t.setId(UUID.randomUUID());
        t.setName("Test Tenant");
        t.setSlug("test-tenant");
        t.setSubscriptionStatus(status);
        t.setSubscriptionPlan(plan);
        t.setSubscriptionEndDate(endDate);
        return t;
    }

    @BeforeEach
    void setUp() {
        lenient().when(tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        lenient().when(tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.PAST_DUE))
                .thenReturn(Collections.emptyList());
        lenient().when(planService.getGracePeriodDaysOrDefault(anyString(), anyInt()))
                .thenReturn(3);
    }

    @Test
    @DisplayName("does nothing when no tenants have active subscriptions")
    void doesNothingWhenNoActiveTenants() {
        job.checkExpiredSubscriptions();
        verify(tenantRepository, never()).save(any());
        verify(revocationService, never()).revokeAllTokensForTenant(any(), any());
    }

    @Nested
    @DisplayName("ACTIVE tenants")
    class ActiveTenants {

        @Test
        @DisplayName("marks tenant as PAST_DUE when expired but within grace period")
        void marksPastDueWithinGrace() {
            Tenant tenant = createTenant(SubscriptionStatus.ACTIVE, SubscriptionPlan.PRO,
                    LocalDate.now().minusDays(2));
            when(tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.ACTIVE))
                    .thenReturn(List.of(tenant));

            job.checkExpiredSubscriptions();

            verify(tenantRepository).save(tenant);
            verify(revocationService, never()).revokeAllTokensForTenant(any(), any());
        }

        @Test
        @DisplayName("suspends tenant when expired past grace period")
        void suspendsPastGrace() {
            Tenant tenant = createTenant(SubscriptionStatus.ACTIVE, SubscriptionPlan.BASIC,
                    LocalDate.now().minusDays(5));
            when(tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.ACTIVE))
                    .thenReturn(List.of(tenant));

            job.checkExpiredSubscriptions();

            verify(tenantRepository).save(tenant);
            verify(revocationService).revokeAllTokensForTenant(eq(tenant.getId()), any(Instant.class));
        }

        @Test
        @DisplayName("does not touch tenant with future end date")
        void doesNotTouchFutureTenant() {
            Tenant tenant = createTenant(SubscriptionStatus.ACTIVE, SubscriptionPlan.PRO,
                    LocalDate.now().plusDays(30));
            when(tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.ACTIVE))
                    .thenReturn(List.of(tenant));

            job.checkExpiredSubscriptions();

            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not touch tenant with null end date")
        void doesNotTouchNullEndDate() {
            Tenant tenant = createTenant(SubscriptionStatus.ACTIVE, SubscriptionPlan.PRO, null);
            when(tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.ACTIVE))
                    .thenReturn(List.of(tenant));

            job.checkExpiredSubscriptions();

            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips already suspended tenants")
        void skipsSuspended() {
            Tenant tenant = createTenant(SubscriptionStatus.ACTIVE, SubscriptionPlan.PRO,
                    LocalDate.now().minusDays(5));
            tenant.setSubscriptionStatus(SubscriptionStatus.SUSPENDED);
            when(tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.ACTIVE))
                    .thenReturn(List.of(tenant));

            job.checkExpiredSubscriptions();

            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("uses default grace period of 3 days")
        void usesDefaultGracePeriod() {
            lenient().when(planService.getGracePeriodDaysOrDefault(anyString(), anyInt()))
                    .thenReturn(3);

            Tenant tenant = createTenant(SubscriptionStatus.ACTIVE, SubscriptionPlan.PRO,
                    LocalDate.now().minusDays(3));
            when(tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.ACTIVE))
                    .thenReturn(List.of(tenant));

            job.checkExpiredSubscriptions();

            verify(planService).getGracePeriodDaysOrDefault("PRO", 3);
        }

        @Test
        @DisplayName("ENTERPRISE plan uses default grace when planService returns default")
        void enterpriseUsesDefaultGrace() {
            lenient().when(planService.getGracePeriodDaysOrDefault("ENTERPRISE", 3))
                    .thenReturn(3);

            Tenant tenant = createTenant(SubscriptionStatus.ACTIVE, SubscriptionPlan.ENTERPRISE,
                    LocalDate.now().minusDays(2));
            when(tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.ACTIVE))
                    .thenReturn(List.of(tenant));

            job.checkExpiredSubscriptions();

            verify(planService).getGracePeriodDaysOrDefault("ENTERPRISE", 3);
        }
    }

    @Nested
    @DisplayName("PAST_DUE tenants")
    class PastDueTenants {

        @Test
        @DisplayName("suspends past_due tenant when past grace period")
        void suspendsPastDue() {
            Tenant tenant = createTenant(SubscriptionStatus.PAST_DUE, SubscriptionPlan.PRO,
                    LocalDate.now().minusDays(7));
            when(tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.PAST_DUE))
                    .thenReturn(List.of(tenant));

            job.checkExpiredSubscriptions();

            verify(tenantRepository).save(tenant);
            verify(revocationService).revokeAllTokensForTenant(eq(tenant.getId()), any(Instant.class));
        }

        @Test
        @DisplayName("does not double-mark already PAST_DUE tenant still in grace")
        void doesNotDoubleMarkPastDue() {
            Tenant tenant = createTenant(SubscriptionStatus.PAST_DUE, SubscriptionPlan.PRO,
                    LocalDate.now().minusDays(1));
            when(tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.PAST_DUE))
                    .thenReturn(List.of(tenant));

            job.checkExpiredSubscriptions();

            verify(tenantRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("multiple tenants")
    class MultipleTenants {

        @Test
        @DisplayName("processes multiple active tenants correctly")
        void processesMultiple() {
            Tenant t1 = createTenant(SubscriptionStatus.ACTIVE, SubscriptionPlan.PRO,
                    LocalDate.now().minusDays(10));
            Tenant t2 = createTenant(SubscriptionStatus.ACTIVE, SubscriptionPlan.BASIC,
                    LocalDate.now().plusDays(5));
            Tenant t3 = createTenant(SubscriptionStatus.ACTIVE, SubscriptionPlan.ENTERPRISE,
                    LocalDate.now().minusDays(1));

            when(tenantRepository.findAllBySubscriptionStatus(SubscriptionStatus.ACTIVE))
                    .thenReturn(List.of(t1, t2, t3));

            job.checkExpiredSubscriptions();

            verify(tenantRepository, times(2)).save(any());
            verify(revocationService, times(1)).revokeAllTokensForTenant(eq(t1.getId()), any());
        }
    }
}
