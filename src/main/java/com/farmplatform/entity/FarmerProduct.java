package com.farmplatform.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "farmer_product")
public class FarmerProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "farmer_product_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "farmer_id")
    public Farmer farmer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    public Product product;

    @Column(name = "available_quantity", precision = 15, scale = 3)
    public BigDecimal availableQuantity;

    public String unit;

    /** The price the farmer asks per unit. Never exposed to customers. */
    @Column(precision = 15, scale = 2)
    public BigDecimal price;

    /** AVAILABLE or UNAVAILABLE */
    @Column(name = "availability_status")
    public String availabilityStatus = "AVAILABLE";
}
