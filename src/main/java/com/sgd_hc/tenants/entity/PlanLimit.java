package com.sgd_hc.tenants.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "plan_limits")
@Table(name = "plan_limits")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanLimit {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_limit_plan"))
    private Plan plan;

    @Column(name = "resource_key", nullable = false, length = 50)
    private String resourceKey;

    @Column(name = "resource_value", nullable = false)
    private Long resourceValue;

    @Column(length = 20)
    private String unit;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanLimit other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
