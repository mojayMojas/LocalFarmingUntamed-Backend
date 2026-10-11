package com.farmplatform.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "customer_order_item")
public class CustomerOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    public CustomerOrder order;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    public Product product;

    /**
     * Added to the ERD so stock can be restored when an unpaid order is cancelled.
     * Never serialised: it leads back to batches and farmers.
     */
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "inventory_id")
    public Inventory inventory;

    @Column(precision = 15, scale = 3)
    public BigDecimal quantity;

    @Column(name = "unit_price", precision = 15, scale = 2)
    public BigDecimal unitPrice;

    @Column(precision = 15, scale = 2)
    public BigDecimal subtotal;
}
