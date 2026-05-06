package com.sgd_hc.users.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "users")
@Table(name = "users")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 10)
    private String username;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "document_type", columnDefinition = "document_type_enum")
    @Builder.Default
    private DocumentType documentType = DocumentType.CI;

    @Column(length = 10)
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
    @Column(columnDefinition = "BOOLEAN DEFAULT true", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "role_user",
            joinColumns = @JoinColumn(name = "user_id",
                    foreignKey = @ForeignKey(name = "fk_role_user_user")),
            inverseJoinColumns = @JoinColumn(name = "role_id",
                    foreignKey = @ForeignKey(name = "fk_role_user_role"))
    )
    private Set<Role> roles = new LinkedHashSet<>();
}
