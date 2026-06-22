package com.sgd_hc.notifications.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.sgd_hc.notifications.entity.UserPushToken;
import com.sgd_hc.notifications.repository.UserPushTokenRepository;
import com.sgd_hc.users.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
public class PushNotificationService {

    private final UserPushTokenRepository userPushTokenRepository;

    public PushNotificationService(UserPushTokenRepository userPushTokenRepository) {
        this.userPushTokenRepository = userPushTokenRepository;
    }

    @Async
    public void sendPushAsync(User recipient, String title, String body, Map<String, String> data) {
        List<UserPushToken> tokens = userPushTokenRepository
                .findByUserIdAndIsActiveTrue(recipient.getId());

        if (tokens.isEmpty()) {
            log.debug("No active push tokens for user {}", recipient.getId());
            return;
        }

        List<String> tokenStrings = tokens.stream()
                .map(UserPushToken::getToken)
                .toList();

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokenStrings)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);

            log.debug("Push sent: success={}, failure={}, tokens={}",
                    response.getSuccessCount(), response.getFailureCount(), tokenStrings.size());

            // Desactivar tokens inválidos
            if (response.getFailureCount() > 0) {
                var responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    var resp = responses.get(i);
                    if (!resp.isSuccessful()) {
                        String failedToken = tokenStrings.get(i);
                        FirebaseMessagingException ex = resp.getException();
                        if (ex != null && isInvalidToken(ex)) {
                            userPushTokenRepository.findByToken(failedToken).ifPresent(t -> {
                                t.setIsActive(false);
                                userPushTokenRepository.save(t);
                                log.info("Deactivated invalid push token for user {}: {}",
                                        recipient.getId(), failedToken);
                            });
                        }
                    }
                }
            }

            // Actualizar lastUsedAt en tokens exitosos
            tokens.forEach(t -> {
                t.setLastUsedAt(OffsetDateTime.now());
                userPushTokenRepository.save(t);
            });

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send push notifications: {}", e.getMessage());
        }
    }

    private boolean isInvalidToken(FirebaseMessagingException e) {
        String code = e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().name() : "";
        return "INVALID_ARGUMENT".equals(code)
                || "UNREGISTERED".equals(code)
                || "NOT_FOUND".equals(code)
                || "MISMATCHED_CREDENTIALS".equals(code);
    }
}
