package com.farmplatform.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "farmer_payment")
public class FarmerPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "farmer_payment_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "farmer_id")
    public Farmer farmer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "purchase_order_id")
    public PurchaseOrder purchaseOrder;

    @Column(precision = 15, scale = 2)
    public BigDecimal amount;

    @Column(name = "payment_date")
    public LocalDate paymentDate;

    @Column(name = "payment_method")
    public String paymentMethod;

    /** PENDING or PAID */
    public String status = "PENDING";

    public String reference;
}
