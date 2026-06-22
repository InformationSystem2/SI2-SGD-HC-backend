package com.sgd_hc.tenants.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "plan_features")
@Table(name = "plan_features")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanFeature {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_feature_plan"))
    private Plan plan;

    @Column(name = "feature_key", nullable = false, length = 50)
    private String featureKey;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Column(length = 255)
    private String description;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanFeature other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
