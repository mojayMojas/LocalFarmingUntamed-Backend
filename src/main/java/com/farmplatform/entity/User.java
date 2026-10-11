package com.farmplatform.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users") // "user" is a reserved word in PostgreSQL
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "role_id")
    public Role role;

    @Column(name = "first_name", nullable = false)
    public String firstName;

    @Column(name = "last_name", nullable = false)
    public String lastName;

    @Column(nullable = false, unique = true)
    public String email;

    public String phone;

    @JsonIgnore
    @Column(nullable = false)
    public String password;

    public String status = "ACTIVE";

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
