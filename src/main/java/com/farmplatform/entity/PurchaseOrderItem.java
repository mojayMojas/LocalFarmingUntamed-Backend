package com.farmplatform.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_item")
public class PurchaseOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_order_item_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "purchase_order_id")
    public PurchaseOrder purchaseOrder;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    public Product product;

    @Column(precision = 15, scale = 3)
    public BigDecimal quantity;

    @Column(name = "unit_price", precision = 15, scale = 2)
    public BigDecimal unitPrice;

    @Column(precision = 15, scale = 2)
    public BigDecimal subtotal;
}
