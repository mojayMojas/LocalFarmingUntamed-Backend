package com.farmplatform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "driver")
public class Driver {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "driver_id")
    public Long id;

    /** Added to the ERD so a driver can log in and update their own deliveries. */
    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true)
    public User user;

    @Column(name = "first_name")
    public String firstName;

    @Column(name = "last_name")
    public String lastName;

    public String phone;

    @Column(name = "vehicle_registration")
    public String vehicleRegistration;

    @Column(name = "vehicle_type")
    public String vehicleType;

    /** ACTIVE or INACTIVE */
    public String status = "ACTIVE";
}
