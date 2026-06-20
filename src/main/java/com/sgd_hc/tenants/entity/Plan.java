package com.sgd_hc.tenants.entity;

import com.sgd_hc.users.entity.RootEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "plans")
@Table(name = "plans")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Plan extends RootEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "price_monthly", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceMonthly;

    @Column(name = "price_yearly", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceYearly;

    @Column(name = "cycle_days", nullable = false)
    @Builder.Default
    private Integer cycleDays = 30;

    @Column(name = "grace_period_days", nullable = false)
    @Builder.Default
    private Integer gracePeriodDays = 3;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PlanLimit> limits = new ArrayList<>();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PlanFeature> features = new ArrayList<>();
}
