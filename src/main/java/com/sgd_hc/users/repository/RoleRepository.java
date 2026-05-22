package com.sgd_hc.users.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sgd_hc.users.entity.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Set<Role> findAllByIdIn(Set<UUID> ids);

    Optional<Role> findByName(String name);

    Optional<Role> findByNameAndTenantId(String name, UUID tenantId);

    boolean existsByName(String name);

    List<Role> findByPermissions_Id(UUID id);

    List<Role> findByIsActiveTrue();

    @Modifying
    @Query(value = "DELETE FROM roles WHERE tenant_id = :tenantId", nativeQuery = true)
    void deleteAllByTenantId(@Param("tenantId") UUID tenantId);

    @Modifying
    @Query(value = "DELETE FROM role_permission WHERE role_id IN (SELECT id FROM roles WHERE tenant_id = :tenantId)", nativeQuery = true)
    void deleteAllRolePermissionByTenantId(@Param("tenantId") UUID tenantId);

    @Modifying
    @Query(value = "DELETE FROM role_user WHERE user_id IN (SELECT id FROM users WHERE tenant_id = :tenantId)", nativeQuery = true)
    void deleteAllRoleUserByTenantId(@Param("tenantId") UUID tenantId);    
}
