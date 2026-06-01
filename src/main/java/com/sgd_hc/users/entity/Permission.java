package com.sgd_hc.users.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity(name = "permissions")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Permission extends RootEntity {

    @Column(nullable = false, unique = true)
    private String name; 

    @Column(nullable = false)
    private String module;

    @Column(nullable = false)
    private String action;

    private String description;

    @Builder.Default
    @Column(columnDefinition = "BOOLEAN DEFAULT true", nullable = false)
    private Boolean isActive = true;
}
