package com.sgd_hc.tenants.entity;

import com.sgd_hc.users.entity.RootEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
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
}
