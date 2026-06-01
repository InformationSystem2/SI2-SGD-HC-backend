package com.sgd_hc.users.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity(name = "role_attribute_permissions")
@Table(uniqueConstraints = {
    @UniqueConstraint(name = "uq_role_entity_attr", columnNames = {"role_id", "entity_name", "attribute_name"})
})
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleAttributePermission extends RootEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "role_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_role_attr_perm_role")
    )
    private Role role;

    @Column(name = "entity_name", nullable = false)
    private String entityName;

    @Column(name = "attribute_name", nullable = false)
    private String attributeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 20)
    private AccessLevel accessLevel;
}
