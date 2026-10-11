package com.farmplatform.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "invoice")
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    public Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "order_id", unique = true)
    public CustomerOrder order;

    @Column(name = "invoice_number", unique = true)
    public String invoiceNumber;

    @Column(precision = 15, scale = 2)
    public BigDecimal amount;

    @Column(name = "issue_date")
    public LocalDate issueDate = LocalDate.now();

    @Column(name = "due_date")
    public LocalDate dueDate;

    /** UNPAID, PAID, CANCELLED */
    public String status = "UNPAID";
}
