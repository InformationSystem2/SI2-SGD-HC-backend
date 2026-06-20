package com.sgd_hc.notifications.service;

import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.notifications.dto.NotificationResponseDto;
import com.sgd_hc.notifications.entity.Notification;
import com.sgd_hc.notifications.entity.NotificationChannel;
import com.sgd_hc.notifications.entity.NotificationType;
import com.sgd_hc.notifications.entity.PushPlatform;
import com.sgd_hc.notifications.entity.UserPushToken;
import com.sgd_hc.notifications.repository.NotificationRepository;
import com.sgd_hc.notifications.repository.UserPushTokenRepository;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.workflow.entity.ReviewTask;
import com.sgd_hc.workflow.entity.ReviewTaskOutcome;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserPushTokenRepository userPushTokenRepository;
    private final TenantResolverService tenantResolverService;

    // ── Creación de notificaciones (llamadas internas desde ReviewTaskService) ─

    @Transactional
    public void notifyTaskAssigned(UUID tenantId, User recipient, Document document, ReviewTask task) {
        String patientName = document.getPatient().getFirstName() + " " + document.getPatient().getLastName();
        create(tenantId, recipient, NotificationType.TASK_ASSIGNED,
                "Tarea de revisión asignada",
                "Se te asignó un documento del paciente " + patientName + " para revisar",
                document.getId(), task.getId());
    }

    @Transactional
    public void notifyTaskCompleted(UUID tenantId, User author, Document document, ReviewTask task) {
        boolean approved = task.getOutcome() == ReviewTaskOutcome.APPROVED;
        String patientName = document.getPatient().getFirstName() + " " + document.getPatient().getLastName();
        create(tenantId, author,
                approved ? NotificationType.TASK_APPROVED : NotificationType.TASK_REJECTED,
                approved ? "Documento aprobado" : "Documento rechazado",
                "El documento del paciente " + patientName + " fue " + (approved ? "aprobado" : "rechazado"),
                document.getId(), task.getId());
    }

    @Transactional
    public void notifyOverdueEscalation(UUID tenantId, User recipient, Document document, ReviewTask task) {
        create(tenantId, recipient, NotificationType.TASK_OVERDUE,
                "Tarea de revisión vencida",
                "La tarea de revisión ha superado su fecha límite",
                document.getId(), task.getId());
    }

    // ── Consultas (user-facing) ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getMyNotifications(Pageable pageable) {
        User user = currentUser();
        UUID tenantId = tenantResolverService.resolve().getId();
        return notificationRepository
                .findByUserIdAndTenantIdOrderByCreatedAtDesc(user.getId(), tenantId, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public long countUnread() {
        User user = currentUser();
        UUID tenantId = tenantResolverService.resolve().getId();
        return notificationRepository.countByUserIdAndTenantIdAndIsReadFalse(user.getId(), tenantId);
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        User user = currentUser();
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Notificación no encontrada: " + notificationId));
        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(OffsetDateTime.now());
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllAsRead() {
        User user = currentUser();
        UUID tenantId = tenantResolverService.resolve().getId();
        notificationRepository.markAllAsRead(user.getId(), tenantId);
    }

    // ── Push tokens ───────────────────────────────────────────────────────────

    @Transactional
    public void registerPushToken(String token, PushPlatform platform) {
        User user = currentUser();
        userPushTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> {
                    existing.setIsActive(true);
                    existing.setLastUsedAt(OffsetDateTime.now());
                    userPushTokenRepository.save(existing);
                },
                () -> {
                    UserPushToken pushToken = UserPushToken.builder()
                            .user(user)
                            .token(token)
                            .platform(platform)
                            .isActive(true)
                            .build();
                    userPushTokenRepository.save(pushToken);
                }
        );
    }

    @Transactional
    public void deregisterPushToken(String token) {
        userPushTokenRepository.findByToken(token).ifPresent(t -> {
            t.setIsActive(false);
            userPushTokenRepository.save(t);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void create(UUID tenantId, User recipient, NotificationType type,
                        String title, String message, UUID documentId, UUID reviewTaskId) {
        Notification notification = Notification.builder()
                .tenantId(tenantId)
                .user(recipient)
                .channel(NotificationChannel.IN_APP)
                .type(type)
                .title(title)
                .message(message)
                .documentId(documentId)
                .reviewTaskId(reviewTaskId)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
        log.debug("Notificación creada: userId={}, tipo={}", recipient.getId(), type);
    }

    private NotificationResponseDto toDto(Notification n) {
        return new NotificationResponseDto(
                n.getId(),
                n.getChannel(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getDocumentId(),
                n.getReviewTaskId(),
                n.getIsRead(),
                n.getCreatedAt(),
                n.getReadAt()
        );
    }

    private User currentUser() {
        Object principal = Objects.requireNonNull(
                SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        if (principal instanceof SecurityUser su) return su.getUser();
        throw new IllegalStateException("No se pudo determinar el usuario autenticado");
    }
}
