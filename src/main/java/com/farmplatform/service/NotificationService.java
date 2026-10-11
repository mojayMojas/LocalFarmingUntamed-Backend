package com.farmplatform.service;

import com.farmplatform.entity.Notification;
import com.farmplatform.entity.User;
import com.farmplatform.repository.Repos.NotificationRepo;
import com.farmplatform.repository.Repos.UserRepo;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final NotificationRepo notifications;
    private final UserRepo users;

    public NotificationService(NotificationRepo notifications, UserRepo users) {
        this.notifications = notifications;
        this.users = users;
    }

    public void notify(User user, String title, String message, String type) {
        if (user == null) {
            return;
        }
        Notification n = new Notification();
        n.user = user;
        n.title = title;
        n.message = message;
        n.type = type;
        notifications.save(n);
    }

    public void notifyAdmins(String title, String message, String type) {
        for (User admin : users.findByRoleRoleName("ADMIN")) {
            notify(admin, title, message, type);
        }
    }
}
