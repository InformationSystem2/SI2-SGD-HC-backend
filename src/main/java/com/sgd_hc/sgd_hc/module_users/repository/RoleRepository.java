package com.sgd_hc.sgd_hc.module_users.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sgd_hc.sgd_hc.module_users.entity.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Set<Role> findAllByIdIn(Set<Long> ids);
    Optional<Role> findByName(String name);
}
