package com.farmplatform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "farmer_location")
public class FarmerLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "farmer_id")
    public Farmer farmer;

    public String province;
    public String municipality;
    public String town;
    public String address;
}
