package com.farmplatform.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "delivery")
public class Delivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    public CustomerOrder order;

    @ManyToOne(optional = false)
    @JoinColumn(name = "driver_id")
    public Driver driver;

    @Column(name = "delivery_address")
    public String deliveryAddress;

    @Column(name = "scheduled_date")
    public LocalDate scheduledDate;

    @Column(name = "delivery_date")
    public LocalDate deliveryDate;

    /** SCHEDULED, IN_TRANSIT, DELIVERED, FAILED */
    public String status = "SCHEDULED";
}
