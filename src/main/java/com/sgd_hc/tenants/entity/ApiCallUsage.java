package com.sgd_hc.tenants.entity;

import com.sgd_hc.users.entity.RootEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity(name = "api_call_usage")
@Table(
        name = "api_call_usage",
        uniqueConstraints = @UniqueConstraint(name = "uq_api_usage_tenant_month", columnNames = {"tenant_id", "year_month"})
)
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiCallUsage extends RootEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_api_usage_tenant"))
    private Tenant tenant;

    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "call_count", nullable = false)
    private long callCount;
}
