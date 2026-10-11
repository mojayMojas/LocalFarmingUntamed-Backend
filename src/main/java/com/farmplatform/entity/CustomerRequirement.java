package com.farmplatform.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_requirement")
public class CustomerRequirement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "requirement_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    public Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    public Product product;

    @Column(precision = 15, scale = 3)
    public BigDecimal quantity;

    public String unit;

    @Column(name = "required_date")
    public LocalDate requiredDate;

    @Column(length = 2000)
    public String specifications;

    /** OPEN, REVIEWED, SOURCING, FULFILLED, CLOSED */
    public String status = "OPEN";

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
