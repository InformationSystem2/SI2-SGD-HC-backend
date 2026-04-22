package com.sgd_hc.sgd_hc.module_patients.entity;

import java.time.LocalDate;

import com.sgd_hc.sgd_hc.module_users.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "patients")
@PrimaryKeyJoinColumn(name = "id")
@DiscriminatorValue("PATIENT")
@Getter
@Setter
@NoArgsConstructor
public class Patient extends User {

    @Column(name = "birth_date")
    private LocalDate birthDate;
}
