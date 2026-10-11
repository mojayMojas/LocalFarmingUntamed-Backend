package com.farmplatform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customer")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    public Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true)
    public User user;

    @Column(name = "company_name", nullable = false)
    public String companyName;

    /** Restaurant, Retailer, Wholesaler, School, Hotel, Caterer, Manufacturer, Other */
    @Column(name = "customer_type")
    public String customerType;

    @Column(name = "business_registration")
    public String businessRegistration;

    public String address;

    /** ACTIVE or SUSPENDED */
    public String status = "ACTIVE";
}
