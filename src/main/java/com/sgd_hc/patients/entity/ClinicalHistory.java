package com.sgd_hc.patients.entity;

import com.sgd_hc.users.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;

@Entity
@Table(name = "clinical_histories")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalHistory extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "patient_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_clinical_histories_patient"))
    private Patient patient;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "blood_type", length = 10)
    private String bloodType;

    @Column(name = "pathological_antecedents", columnDefinition = "TEXT")
    private String pathologicalAntecedents;

    @Column(name = "non_pathological_antecedents", columnDefinition = "TEXT")
    private String nonPathologicalAntecedents;

    @Column(name = "family_antecedents", columnDefinition = "TEXT")
    private String familyAntecedents;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allergies", columnDefinition = "jsonb")
    private List<Allergy> allergies;

    @Column(name = "chronic_conditions", columnDefinition = "TEXT")
    private String chronicConditions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_medications", columnDefinition = "jsonb")
    private List<Medication> currentMedications;

    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;
}
