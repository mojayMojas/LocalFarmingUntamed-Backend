package com.farmplatform.service;

import com.farmplatform.common.ApiException;
import com.farmplatform.dto.Dtos.*;
import com.farmplatform.entity.Customer;
import com.farmplatform.entity.Driver;
import com.farmplatform.entity.Farmer;
import com.farmplatform.entity.FarmerLocation;
import com.farmplatform.entity.User;
import com.farmplatform.repository.Repos.*;
import com.farmplatform.security.AuthUser;
import com.farmplatform.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepo users;
    private final RoleRepo roles;
    private final FarmerRepo farmers;
    private final FarmerLocationRepo locations;
    private final CustomerRepo customers;
    private final DriverRepo drivers;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final NotificationService notifications;

    public AuthService(UserRepo users, RoleRepo roles, FarmerRepo farmers, FarmerLocationRepo locations,
                       CustomerRepo customers, DriverRepo drivers, PasswordEncoder encoder, JwtService jwt,
                       NotificationService notifications) {
        this.users = users;
        this.roles = roles;
        this.farmers = farmers;
        this.locations = locations;
        this.customers = customers;
        this.drivers = drivers;
        this.encoder = encoder;
        this.jwt = jwt;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!encoder.matches(req.password(), user.password)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!"ACTIVE".equals(user.status)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is not active. Please contact the platform owner.");
        }
        return toResponse(user, jwt.generate(user));
    }

    @Transactional
    public AuthResponse registerFarmer(RegisterFarmerRequest req) {
        User user = newUser("FARMER", req.firstName(), req.lastName(), req.email(), req.phone(), req.password());
        Farmer farmer = new Farmer();
        farmer.user = user;
        farmers.save(farmer);

        if (notBlank(req.province()) || notBlank(req.town()) || notBlank(req.address()) || notBlank(req.municipality())) {
            FarmerLocation loc = new FarmerLocation();
            loc.farmer = farmer;
            loc.province = req.province();
            loc.municipality = req.municipality();
            loc.town = req.town();
            loc.address = req.address();
            locations.save(loc);
        }
        notifications.notifyAdmins("New farmer registration",
                req.firstName() + " " + req.lastName() + " registered and is waiting to be verified.", "SUPPLY");
        return toResponse(user, jwt.generate(user));
    }

    @Transactional
    public AuthResponse registerCustomer(RegisterCustomerRequest req) {
        User user = newUser("CUSTOMER", req.firstName(), req.lastName(), req.email(), req.phone(), req.password());
        Customer customer = new Customer();
        customer.user = user;
        customer.companyName = req.companyName();
        customer.customerType = req.customerType();
        customer.businessRegistration = req.businessRegistration();
        customer.address = req.address();
        customers.save(customer);
        notifications.notifyAdmins("New customer registration", req.companyName() + " registered as a bulk customer.", "SYSTEM");
        return toResponse(user, jwt.generate(user));
    }

    /** Used by the administrator to create driver logins. */
    @Transactional
    public Driver createDriver(CreateDriverRequest req) {
        User user = newUser("DRIVER", req.firstName(), req.lastName(), req.email(), req.phone(), req.password());
        Driver driver = new Driver();
        driver.user = user;
        driver.firstName = req.firstName();
        driver.lastName = req.lastName();
        driver.phone = req.phone();
        driver.vehicleRegistration = req.vehicleRegistration();
        driver.vehicleType = req.vehicleType();
        return drivers.save(driver);
    }

    @Transactional(readOnly = true)
    public AuthResponse me(AuthUser principal) {
        User user = users.findById(principal.userId()).orElseThrow(() -> ApiException.notFound("User not found"));
        return toResponse(user, null);
    }

    private User newUser(String roleName, String first, String last, String email, String phone, String password) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User();
        user.role = roles.findByRoleName(roleName).orElseThrow(() -> new IllegalStateException("Role missing: " + roleName));
        user.firstName = first.trim();
        user.lastName = last.trim();
        user.email = normalized;
        user.phone = phone;
        user.password = encoder.encode(password);
        return users.save(user);
    }

    private AuthResponse toResponse(User user, String token) {
        String profileStatus = switch (user.role.roleName) {
            case "FARMER" -> farmers.findByUserId(user.id).map(f -> f.verificationStatus).orElse(null);
            case "CUSTOMER" -> customers.findByUserId(user.id).map(c -> c.status).orElse(null);
            case "DRIVER" -> drivers.findByUserId(user.id).map(d -> d.status).orElse(null);
            default -> null;
        };
        return new AuthResponse(token, user.id, user.firstName, user.lastName, user.email, user.phone,
                user.role.roleName, profileStatus);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
