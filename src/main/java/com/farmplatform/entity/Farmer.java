package com.farmplatform.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "farmer")
public class Farmer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "farmer_id")
    public Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true)
    public User user;

    /** PENDING, VERIFIED, REJECTED, SUSPENDED */
    @Column(name = "verification_status")
    public String verificationStatus = "PENDING";

    @Column(name = "registration_date")
    public LocalDate registrationDate = LocalDate.now();
}
