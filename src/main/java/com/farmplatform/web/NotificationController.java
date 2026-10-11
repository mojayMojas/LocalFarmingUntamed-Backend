package com.farmplatform.web;

import com.farmplatform.common.ApiException;
import com.farmplatform.entity.Notification;
import com.farmplatform.repository.Repos.NotificationRepo;
import com.farmplatform.security.AuthUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepo notifications;

    public NotificationController(NotificationRepo notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<Notification> mine(@AuthenticationPrincipal AuthUser me) {
        return notifications.findByUserId(me.userId());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthUser me) {
        long n = notifications.findByUserId(me.userId()).stream().filter(x -> "UNREAD".equals(x.status)).count();
        return Map.of("count", n);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Notification n = notifications.findById(id).orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (!n.user.id.equals(me.userId())) {
            throw ApiException.notFound("Notification not found");
        }
        n.status = "READ";
        notifications.save(n);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal AuthUser me) {
        List<Notification> unread = notifications.findByUserId(me.userId()).stream()
                .filter(x -> "UNREAD".equals(x.status)).toList();
        unread.forEach(x -> x.status = "READ");
        notifications.saveAll(unread);
        return ResponseEntity.noContent().build();
    }
}
