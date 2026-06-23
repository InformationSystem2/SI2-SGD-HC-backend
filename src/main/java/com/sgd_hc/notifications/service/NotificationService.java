package com.sgd_hc.notifications.service;

import com.sgd_hc.config.mail.EmailService;
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
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.PlanFeatureValidator;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import com.sgd_hc.workflow.entity.ReviewTask;
import com.sgd_hc.workflow.entity.ReviewTaskOutcome;
import com.sgd_hc.workflow.repository.ReviewTaskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserPushTokenRepository userPushTokenRepository;
    private final TenantResolverService tenantResolverService;
    private final EmailService emailService;
    private final ObjectProvider<PushNotificationService> pushNotificationService;
    private final PlanFeatureValidator planFeatureValidator;
    private final ReviewTaskRepository reviewTaskRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserPushTokenRepository userPushTokenRepository,
            TenantResolverService tenantResolverService,
            EmailService emailService,
            ObjectProvider<PushNotificationService> pushNotificationService,
            PlanFeatureValidator planFeatureValidator,
            ReviewTaskRepository reviewTaskRepository) {
        this.notificationRepository = notificationRepository;
        this.userPushTokenRepository = userPushTokenRepository;
        this.tenantResolverService = tenantResolverService;
        this.emailService = emailService;
        this.pushNotificationService = pushNotificationService;
        this.planFeatureValidator = planFeatureValidator;
        this.reviewTaskRepository = reviewTaskRepository;
    }

    // ── Creación de notificaciones (llamadas internas desde ReviewTaskService) ─

    @Transactional
    public void notifyTaskAssigned(UUID tenantId, User recipient, Document document, ReviewTask task) {
        String patientName = document.getPatient().getFirstName() + " " + document.getPatient().getLastName();
        String assignerName = document.getUploader().getFirstName() + " " + document.getUploader().getLastName();

        create(tenantId, recipient, NotificationType.TASK_ASSIGNED,
                "Tarea de revisión asignada",
                "Se te asignó un documento del paciente " + patientName + " para revisar",
                document.getId(), task.getId());

        dispatchAdditionalChannels(tenantId, recipient, NotificationType.TASK_ASSIGNED,
                "Tarea de revisión asignada",
                "Se te asignó un documento del paciente " + patientName + " para revisar",
                document.getId(), task.getId(),
                Map.of("patientName", patientName, "assignerName", assignerName));
    }

    @Transactional
    public void notifyTaskCompleted(UUID tenantId, User author, Document document, ReviewTask task) {
        boolean approved = task.getOutcome() == ReviewTaskOutcome.APPROVED;
        String patientName = document.getPatient().getFirstName() + " " + document.getPatient().getLastName();

        NotificationType type = approved ? NotificationType.TASK_APPROVED : NotificationType.TASK_REJECTED;
        String title = approved ? "Documento aprobado" : "Documento rechazado";
        String message = "El documento del paciente " + patientName + " fue " + (approved ? "aprobado" : "rechazado");

        create(tenantId, author, type, title, message, document.getId(), task.getId());

        String reason = approved ? null : (task.getCompletedBy() != null ? message : null);
        dispatchAdditionalChannels(tenantId, author, type, title, message,
                document.getId(), task.getId(),
                Map.of("patientName", patientName, "approved", String.valueOf(approved)));
    }

    @Transactional
    public void notifyOverdueEscalation(UUID tenantId, User recipient, Document document, ReviewTask task) {
        String patientName = document.getPatient().getFirstName() + " " + document.getPatient().getLastName();

        create(tenantId, recipient, NotificationType.TASK_OVERDUE,
                "Tarea de revisión vencida",
                "La tarea de revisión ha superado su fecha límite",
                document.getId(), task.getId());

        dispatchAdditionalChannels(tenantId, recipient, NotificationType.TASK_OVERDUE,
                "Tarea de revisión vencida",
                "La tarea de revisión ha superado su fecha límite",
                document.getId(), task.getId(),
                Map.of("patientName", patientName));
    }

    @Transactional
    public void sendTestNotification(NotificationType type, String title, String message) {
        User user = currentUser();
        UUID tenantId = tenantResolverService.resolve().getId();
        create(tenantId, user, type, title, message, null, null);
        dispatchAdditionalChannels(tenantId, user, type, title, message, null, null, Map.of());
    }


    @Transactional
    public void sendNotificationToUser(User recipient, NotificationType type, String title, String message) {
        UUID tenantId = recipient.getTenant() != null ? recipient.getTenant().getId() : tenantResolverService.resolve().getId();
        create(tenantId, recipient, type, title, message, null, null);
        dispatchAdditionalChannels(tenantId, recipient, type, title, message, null, null, Map.of());
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

    @SuppressWarnings("unchecked")
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
        log.debug("Notificación IN_APP creada: userId={}, tipo={}", recipient.getId(), type);
    }

    private void dispatchAdditionalChannels(UUID tenantId, User recipient, NotificationType type,
                                            String title, String message, UUID documentId, UUID reviewTaskId,
                                            Map<String, String> templateData) {
        try {
            Tenant tenant = tenantResolverService.resolve();
            Map<String, Object> settings = tenant.getSettings();
            if (settings == null) return;

            Object notifSettingsObj = settings.get("notifications");
            if (!(notifSettingsObj instanceof Map)) return;

            Map<String, Object> notifSettings = (Map<String, Object>) notifSettingsObj;

            // ── Email ──
            Boolean emailEnabled = (Boolean) notifSettings.getOrDefault("emailEnabled", false);
            if (Boolean.TRUE.equals(emailEnabled) && recipient.getEmail() != null) {
                try {
                    planFeatureValidator.checkEmailNotifications(tenantId);
                    sendNotificationEmail(recipient.getEmail(), type, templateData);
                } catch (Exception e) {
                    log.debug("Email notifications not allowed for tenant {}: {}", tenantId, e.getMessage());
                }
            }

            // ── Push ──
            Boolean pushEnabled = (Boolean) notifSettings.getOrDefault("pushEnabled", false);
            if (Boolean.TRUE.equals(pushEnabled)) {
                pushNotificationService.ifAvailable(service ->
                    service.sendPushAsync(recipient, title, message,
                        Map.of("type", type.name(),
                                "documentId", documentId != null ? documentId.toString() : "",
                                "reviewTaskId", reviewTaskId != null ? reviewTaskId.toString() : ""))
                );
            }
        } catch (Exception e) {
            log.warn("Error dispatching additional channels for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    private void sendNotificationEmail(String to, NotificationType type, Map<String, String> data) {
        String patientName = data.getOrDefault("patientName", "desconocido");

        try {
            switch (type) {
                case TASK_ASSIGNED -> {
                    String assignerName = data.getOrDefault("assignerName", "un usuario");
                    emailService.sendTaskAssignedEmail(to, patientName, assignerName);
                }
                case TASK_APPROVED -> emailService.sendTaskApprovedEmail(to, patientName);
                case TASK_REJECTED -> emailService.sendTaskRejectedEmail(to, patientName, null);
                case TASK_OVERDUE -> emailService.sendTaskOverdueEmail(to, patientName);
                default -> log.debug("No hay template email para el tipo {}", type);
            }
            log.debug("Email de notificación enviado: to={}, type={}", to, type);
        } catch (Exception e) {
            log.warn("Error enviando email de notificación a {}: {}", to, e.getMessage());
        }
    }

    private NotificationResponseDto toDto(Notification n) {
        UUID workflowId = null;
        if (n.getReviewTaskId() != null) {
            ReviewTask task = reviewTaskRepository.findById(n.getReviewTaskId()).orElse(null);
            if (task != null && task.getWorkflow() != null) {
                workflowId = task.getWorkflow().getId();
            }
        }
        return new NotificationResponseDto(
                n.getId(),
                n.getChannel(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getDocumentId(),
                n.getReviewTaskId(),
                workflowId,
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
