package com.sgd_hc.users.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sgd_hc.users.entity.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Set<Role> findAllByIdIn(Set<UUID> ids);

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    List<Role> findByPermissions_Id(UUID id);

    List<Role> findByIsActiveTrue();
}
