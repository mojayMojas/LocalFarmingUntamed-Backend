package com.farmplatform.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "processing_id")
    public ProcessingBatch processingBatch;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    public Product product;

    @Column(precision = 15, scale = 3)
    public BigDecimal quantity;

    public String unit;

    @Column(name = "storage_location")
    public String storageLocation;

    /** Customer-facing price per unit for this stock line (added to the ERD: price lives on INVENTORY). */
    @Column(name = "selling_price", precision = 15, scale = 2)
    public BigDecimal sellingPrice;

    /** AVAILABLE, OUT_OF_STOCK, HIDDEN */
    public String status = "AVAILABLE";

    @Column(name = "updated_at")
    public LocalDateTime updatedAt = LocalDateTime.now();
}
