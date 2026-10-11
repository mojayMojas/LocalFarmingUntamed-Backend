package com.farmplatform.service;

import com.farmplatform.common.ApiException;
import com.farmplatform.entity.Customer;
import com.farmplatform.entity.Driver;
import com.farmplatform.entity.Farmer;
import com.farmplatform.entity.User;
import com.farmplatform.repository.Repos.CustomerRepo;
import com.farmplatform.repository.Repos.DriverRepo;
import com.farmplatform.repository.Repos.FarmerRepo;
import com.farmplatform.repository.Repos.UserRepo;
import com.farmplatform.security.AuthUser;
import org.springframework.stereotype.Service;

/** Resolves the logged-in user into their farmer / customer / driver profile. */
@Service
public class CurrentActors {
    private final FarmerRepo farmers;
    private final CustomerRepo customers;
    private final DriverRepo drivers;
    private final UserRepo users;

    public CurrentActors(FarmerRepo farmers, CustomerRepo customers, DriverRepo drivers, UserRepo users) {
        this.farmers = farmers;
        this.customers = customers;
        this.drivers = drivers;
        this.users = users;
    }

    public User user(AuthUser me) {
        return users.findById(me.userId()).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    public Farmer farmer(AuthUser me) {
        return farmers.findByUserId(me.userId()).orElseThrow(() -> ApiException.forbidden("No farmer profile for this account"));
    }

    public Customer customer(AuthUser me) {
        return customers.findByUserId(me.userId()).orElseThrow(() -> ApiException.forbidden("No customer profile for this account"));
    }

    public Driver driver(AuthUser me) {
        return drivers.findByUserId(me.userId()).orElseThrow(() -> ApiException.forbidden("No driver profile for this account"));
    }
}
