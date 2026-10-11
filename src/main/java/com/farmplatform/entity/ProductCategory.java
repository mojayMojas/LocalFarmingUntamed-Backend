package com.farmplatform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "product_category")
public class ProductCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    public Long id;

    @Column(name = "category_name", nullable = false, unique = true)
    public String categoryName;

    public String description;
}
