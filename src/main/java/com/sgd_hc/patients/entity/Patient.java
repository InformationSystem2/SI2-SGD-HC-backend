package com.sgd_hc.patients.entity;

import com.sgd_hc.users.entity.DocumentType;
import com.sgd_hc.users.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Entity
@Table(name = "patients")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Patient extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "document_type", columnDefinition = "document_type_enum")
    @Builder.Default
    private DocumentType documentType = DocumentType.CI;

    @Column(length = 10)
    private String documentNumber;

    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String address;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "gender", columnDefinition = "gender_enum", nullable = false)
    private Gender gender;

    @Column(nullable = false)
    private LocalDate birthDate;
}
