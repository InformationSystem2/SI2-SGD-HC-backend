package com.sgd_hc.backups.entity;

import com.sgd_hc.users.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "backup_history")
@Getter
@Setter
public class BackupHistory extends BaseEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "backup_type", nullable = false)
    private String backupType = "tenant";

    @Column(name = "created_by")
    private UUID createdBy;
}
