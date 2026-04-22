package com.sgd_hc.sgd_hc.module_users.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity(name = "users")
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING, length = 31)
@DiscriminatorValue("USER")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String username;

    @Column(name = "document_type", length = 20)
    private String documentType;

    @Column(name = "document_number", length = 50)
    private String documentNumber;

    @Column(nullable = false, unique = true, length = 50)
    private String email;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(nullable = false, length = 255)
    private String password;

    private String phone;

    @Column(nullable = false)
    private String gender;

    @Builder.Default
    @Column(columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean isActive = true;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(
        name = "role_user",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
}
