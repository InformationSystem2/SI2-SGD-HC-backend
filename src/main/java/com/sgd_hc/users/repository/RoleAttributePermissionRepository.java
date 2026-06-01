package com.sgd_hc.users.repository;

import com.sgd_hc.users.entity.RoleAttributePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface RoleAttributePermissionRepository extends JpaRepository<RoleAttributePermission, UUID> {
    List<RoleAttributePermission> findByRoleId(UUID roleId);
}
