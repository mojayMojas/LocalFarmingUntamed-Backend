package com.farmplatform.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "customer_order")
public class CustomerOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    public Customer customer;

    @Column(name = "order_date")
    public LocalDate orderDate = LocalDate.now();

    /** PENDING_PAYMENT, PAID, SCHEDULED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED */
    public String status = "PENDING_PAYMENT";

    @Column(name = "total_amount", precision = 15, scale = 2)
    public BigDecimal totalAmount = BigDecimal.ZERO;
}
