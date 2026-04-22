package com.sgd_hc.sgd_hc.module_users.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sgd_hc.sgd_hc.module_users.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByEmail(String email);

    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);

    Optional<User> findByDocumentNumber(String documentNumber);
    boolean existsByDocumentNumber(String documentNumber);

    @Query("SELECT u FROM users u WHERE TYPE(u) = com.sgd_hc.sgd_hc.module_users.entity.User")
    List<User> findAllRegularUsers();
}
