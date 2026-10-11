package com.farmplatform.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "quality_inspection")
public class QualityInspection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "batch_id")
    public ReceivingBatch batch;

    @ManyToOne(optional = false)
    @JoinColumn(name = "inspected_by")
    public User inspectedBy;

    @Column(name = "inspection_date")
    public LocalDate inspectionDate = LocalDate.now();

    /** PASSED or FAILED */
    public String result;

    @Column(length = 2000)
    public String notes;
}
