package com.farmplatform.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_order_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "farmer_id")
    public Farmer farmer;

    @Column(name = "order_date")
    public LocalDate orderDate = LocalDate.now();

    /** PENDING (awaiting farmer), CONFIRMED, DECLINED, RECEIVED, ACCEPTED, REJECTED, CANCELLED */
    public String status = "PENDING";

    @Column(name = "total_amount", precision = 15, scale = 2)
    public BigDecimal totalAmount = BigDecimal.ZERO;
}
