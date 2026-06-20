package com.sgd_hc.notifications.repository;

import com.sgd_hc.notifications.entity.UserPushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPushTokenRepository extends JpaRepository<UserPushToken, UUID> {

    List<UserPushToken> findByUserIdAndIsActiveTrue(UUID userId);

    Optional<UserPushToken> findByToken(String token);
}
