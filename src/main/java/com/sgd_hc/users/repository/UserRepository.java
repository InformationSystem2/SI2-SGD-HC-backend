package com.sgd_hc.users.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sgd_hc.users.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByEmail(String email);

    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);

    Optional<User> findByDocumentNumber(String documentNumber);
    boolean existsByDocumentNumber(String documentNumber);

    @Query("SELECT u FROM users u")
    List<User> findAllRegularUsers();
}
