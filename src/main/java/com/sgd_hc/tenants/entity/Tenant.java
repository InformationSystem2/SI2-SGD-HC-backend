package com.sgd_hc.tenants.entity;

import com.sgd_hc.users.entity.RootEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.Map;

@Entity(name = "tenants")
@Table(
        name = "tenants",
        uniqueConstraints = @UniqueConstraint(name = "uq_tenants_slug", columnNames = "slug")
)
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tenant extends RootEntity {
    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, length = 50)
    private String slug;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String address;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "subscription_plan", columnDefinition = "subscription_plan_enum")
    private SubscriptionPlan subscriptionPlan;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "subscription_status", columnDefinition = "subscription_status_enum")
    private SubscriptionStatus subscriptionStatus;

    @Column(nullable = false)
    private LocalDate subscriptionStartDate;

    @Column(name = "subscription_end_date")
    private LocalDate subscriptionEndDate;

    @Column(name = "billing_cycle", nullable = false, length = 10)
    @Builder.Default
    private String billingCycle = "MONTHLY";

    public boolean isSuspended() {
        return subscriptionStatus == SubscriptionStatus.SUSPENDED;
    }

    public boolean isActive() {
        return subscriptionStatus == SubscriptionStatus.ACTIVE;
    }
}