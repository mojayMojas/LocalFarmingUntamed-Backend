package com.farmplatform.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "processing_batch")
public class ProcessingBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "processing_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "input_batch_id")
    public ReceivingBatch inputBatch;

    /** The finished (customer-ready) product this run yields. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    public Product product;

    /** e.g. cleaning, sorting, grading, packaging, slaughter, pasteurising */
    @Column(name = "processing_type")
    public String processingType;

    @Column(name = "quantity_in", precision = 15, scale = 3)
    public BigDecimal quantityIn;

    @Column(name = "quantity_out", precision = 15, scale = 3)
    public BigDecimal quantityOut;

    @Column(name = "processed_date")
    public LocalDate processedDate;

    /** IN_PROGRESS or COMPLETED */
    public String status = "IN_PROGRESS";
}
