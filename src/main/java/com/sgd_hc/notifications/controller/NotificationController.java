package com.sgd_hc.notifications.controller;

import com.sgd_hc.notifications.dto.NotificationResponseDto;
import com.sgd_hc.notifications.dto.RegisterPushTokenRequestDto;
import com.sgd_hc.notifications.service.NotificationService;
import com.sgd_hc.notifications.entity.NotificationType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAuthority('notification:read')")
    public ResponseEntity<Page<NotificationResponseDto>> getMyNotifications(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(notificationService.getMyNotifications(pageable));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notification:read')")
    public ResponseEntity<Map<String, Long>> countUnread() {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread()));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notification:update')")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:update')")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/push-tokens")
    @PreAuthorize("hasAuthority('notification:push:create')")
    public ResponseEntity<Void> registerPushToken(
            @Valid @RequestBody RegisterPushTokenRequestDto dto) {
        notificationService.registerPushToken(dto.token(), dto.platform());
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @DeleteMapping("/push-tokens/{token}")
    @PreAuthorize("hasAuthority('notification:push:delete')")
    public ResponseEntity<Void> deregisterPushToken(@PathVariable String token) {
        notificationService.deregisterPushToken(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority('notification:read')")
    public ResponseEntity<Void> sendTestNotification(
            @RequestParam NotificationType type,
            @RequestParam String title,
            @RequestParam String message) {
        notificationService.sendTestNotification(type, title, message);
        return ResponseEntity.ok().build();
    }
}
