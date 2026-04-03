package com.sgd_hc.sgd_hc.module_users.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "roles")
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Role {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; 

    @Column(nullable = false, unique = true)
    private String name; 

    private String description;

    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")    
    )
    private Set<Permission> permissions = new HashSet<>();
}