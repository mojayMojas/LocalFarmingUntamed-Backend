package com.farmplatform.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "receiving_batch")
public class ReceivingBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "purchase_order_id")
    public PurchaseOrder purchaseOrder;

    @Column(name = "batch_number", unique = true)
    public String batchNumber;

    @Column(name = "received_date")
    public LocalDate receivedDate = LocalDate.now();

    @Column(precision = 15, scale = 3)
    public BigDecimal quantity;

    public String unit;

    /** PENDING, ACCEPTED, REJECTED */
    @Column(name = "quality_status")
    public String qualityStatus = "PENDING";
}
