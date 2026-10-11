package com.farmplatform.web;

import com.farmplatform.common.ApiException;
import com.farmplatform.dto.Dtos.*;
import com.farmplatform.entity.Customer;
import com.farmplatform.entity.Driver;
import com.farmplatform.entity.Farmer;
import com.farmplatform.entity.FarmerProduct;
import com.farmplatform.repository.Repos.*;
import com.farmplatform.service.AuthService;
import com.farmplatform.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Administrator: farmers, customers, drivers. */
@RestController
@RequestMapping("/api/admin")
public class AdminPeopleController {
    private static final Set<String> FARMER_STATUSES = Set.of("PENDING", "VERIFIED", "REJECTED", "SUSPENDED");
    private static final Set<String> CUSTOMER_STATUSES = Set.of("ACTIVE", "SUSPENDED");
    private static final Set<String> DRIVER_STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final FarmerRepo farmers;
    private final FarmerLocationRepo locations;
    private final FarmerProductRepo farmerProducts;
    private final CustomerRepo customers;
    private final DriverRepo drivers;
    private final UserRepo users;
    private final AuthService auth;
    private final NotificationService notifications;

    public AdminPeopleController(FarmerRepo farmers, FarmerLocationRepo locations, FarmerProductRepo farmerProducts,
                                 CustomerRepo customers, DriverRepo drivers, UserRepo users, AuthService auth,
                                 NotificationService notifications) {
        this.farmers = farmers;
        this.locations = locations;
        this.farmerProducts = farmerProducts;
        this.customers = customers;
        this.drivers = drivers;
        this.users = users;
        this.auth = auth;
        this.notifications = notifications;
    }

    // ---------- farmers ----------

    @GetMapping("/farmers")
    public List<Farmer> farmers() {
        return farmers.findAll();
    }

    @GetMapping("/farmers/{id}")
    public Map<String, Object> farmer(@PathVariable Long id) {
        Farmer f = farmers.findById(id).orElseThrow(() -> ApiException.notFound("Farmer not found"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("farmer", f);
        m.put("locations", locations.findByFarmerId(f.id));
        m.put("products", farmerProducts.findByFarmerId(f.id));
        return m;
    }

    /** status: PENDING, VERIFIED, REJECTED, SUSPENDED */
    @PutMapping("/farmers/{id}/status")
    public Farmer setFarmerStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        String s = req.status().trim().toUpperCase();
        if (!FARMER_STATUSES.contains(s)) {
            throw ApiException.badRequest("status must be one of " + FARMER_STATUSES);
        }
        Farmer f = farmers.findById(id).orElseThrow(() -> ApiException.notFound("Farmer not found"));
        f.verificationStatus = s;
        farmers.save(f);
        notifications.notify(f.user, "Account " + s.toLowerCase(),
                "Your farmer account status is now " + s + ".", "SYSTEM");
        return f;
    }

    /** What farmers say they can supply (with their asking price) - used when sourcing. Admin only. */
    @GetMapping("/farmer-products")
    public List<FarmerProduct> farmerProducts(@RequestParam(required = false) Long farmerId) {
        return farmerId == null ? farmerProducts.findAll() : farmerProducts.findByFarmerId(farmerId);
    }

    // ---------- customers ----------

    @GetMapping("/customers")
    public List<Customer> customers() {
        return customers.findAll();
    }

    @PutMapping("/customers/{id}/status")
    public Customer setCustomerStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        String s = req.status().trim().toUpperCase();
        if (!CUSTOMER_STATUSES.contains(s)) {
            throw ApiException.badRequest("status must be ACTIVE or SUSPENDED");
        }
        Customer c = customers.findById(id).orElseThrow(() -> ApiException.notFound("Customer not found"));
        c.status = s;
        customers.save(c);
        return c;
    }

    // ---------- drivers ----------

    @GetMapping("/drivers")
    public List<Driver> drivers() {
        return drivers.findAll();
    }

    @PostMapping("/drivers")
    @ResponseStatus(HttpStatus.CREATED)
    public Driver createDriver(@Valid @RequestBody CreateDriverRequest req) {
        return auth.createDriver(req);
    }

    @PutMapping("/drivers/{id}")
    public Driver updateDriver(@PathVariable Long id, @RequestBody UpdateDriverRequest req) {
        Driver d = drivers.findById(id).orElseThrow(() -> ApiException.notFound("Driver not found"));
        if (req.firstName() != null && !req.firstName().isBlank()) d.firstName = req.firstName().trim();
        if (req.lastName() != null && !req.lastName().isBlank()) d.lastName = req.lastName().trim();
        if (req.phone() != null) d.phone = req.phone();
        if (req.vehicleRegistration() != null) d.vehicleRegistration = req.vehicleRegistration();
        if (req.vehicleType() != null) d.vehicleType = req.vehicleType();
        if (req.status() != null && !req.status().isBlank()) {
            String s = req.status().trim().toUpperCase();
            if (!DRIVER_STATUSES.contains(s)) {
                throw ApiException.badRequest("status must be ACTIVE or INACTIVE");
            }
            d.status = s;
            // An inactive driver can no longer log in.
            d.user.status = s.equals("ACTIVE") ? "ACTIVE" : "SUSPENDED";
            users.save(d.user);
        }
        return drivers.save(d);
    }
}
