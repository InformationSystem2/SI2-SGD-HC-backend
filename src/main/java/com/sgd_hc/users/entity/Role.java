package com.sgd_hc.users.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity(name = "roles")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Builder.Default
    @Column(columnDefinition = "BOOLEAN DEFAULT true", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id",
                    foreignKey = @ForeignKey(name = "fk_role_permission_role")),
            inverseJoinColumns = @JoinColumn(name = "permission_id",
                    foreignKey = @ForeignKey(name = "fk_role_permission_permission")
            ))
    private Set<Permission> permissions = new LinkedHashSet<>();

}