package com.sgd_hc.sgd_hc.module_users.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "permissions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; 

    @Column(nullable = false, unique = true)
    private String name; 

    @Column(nullable = false)
    private String module;

    @Column(nullable = false)
    private String action;

    private String description;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @PrePersist
    protected void onCreate() {
        if (this.active == null) {
            this.active = true;
        }
    }
}
