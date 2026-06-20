package com.sgd_hc.notifications.dto;

import com.sgd_hc.notifications.entity.NotificationChannel;
import com.sgd_hc.notifications.entity.NotificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponseDto(
        UUID id,
        NotificationChannel channel,
        NotificationType type,
        String title,
        String message,
        UUID documentId,
        UUID reviewTaskId,
        Boolean isRead,
        OffsetDateTime createdAt,
        OffsetDateTime readAt
) {}
