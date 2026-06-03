package com.sgd_hc.users.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sgd_hc.users.entity.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByName(String name);

    boolean existsByName(String name);

    List<Permission> findByIdIn(Set<Long> ids);

    Optional<Permission> findByModuleAndAction(String module, String action);

    List<Permission> findByIsActiveTrue();
}
