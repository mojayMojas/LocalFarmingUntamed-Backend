package com.farmplatform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "product")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id")
    public ProductCategory category;

    @Column(name = "product_name", nullable = false)
    public String productName;

    /** CROP or ANIMAL */
    @Column(name = "product_type")
    public String productType;

    public String description;

    /** kg, litre, dozen, ... */
    public String unit;
}
