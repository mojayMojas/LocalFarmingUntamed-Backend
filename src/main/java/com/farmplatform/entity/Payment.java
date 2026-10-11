package com.farmplatform.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    public CustomerOrder order;

    /** Our TransactionReference sent to Ozow. */
    @Column(name = "ozow_reference", unique = true, nullable = false)
    public String ozowReference;

    /** Ozow's own TransactionId, filled in from the notification. */
    @Column(name = "ozow_transaction_id")
    public String ozowTransactionId;

    @Column(precision = 15, scale = 2)
    public BigDecimal amount;

    /** OZOW or OZOW_MOCK */
    @Column(name = "payment_method")
    public String paymentMethod = "OZOW";

    /** PENDING, COMPLETE, FAILED, CANCELLED */
    @Column(name = "payment_status")
    public String paymentStatus = "PENDING";

    @Column(name = "paid_at")
    public LocalDateTime paidAt;
}
