package com.sgd_hc.users.entity;

import com.sgd_hc.tenants.entity.Tenant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import java.util.UUID;

/**
 * Clase base para todas las entidades ligadas a un tenant.
 **/
@MappedSuperclass
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
// Declaración del filtro tenant
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = UUID.class),
        defaultCondition = "tenant_id = :tenantId"
)
// Activa el filtro en esta tabla al hacer queries con la sesión activa
@Filter(name = "tenantFilter")
public abstract class BaseEntity extends RootEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "tenant_id",
            nullable = false,
            updatable = false
    )
    private Tenant tenant;
}
