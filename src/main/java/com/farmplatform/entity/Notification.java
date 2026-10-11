package com.farmplatform.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    public Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    public User user;

    public String title;

    @Column(length = 2000)
    public String message;

    /** ORDER, PAYMENT, SUPPLY, DELIVERY, SYSTEM */
    public String type;

    /** UNREAD or READ */
    public String status = "UNREAD";

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
