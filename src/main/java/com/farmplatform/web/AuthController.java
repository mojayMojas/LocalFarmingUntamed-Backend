package com.farmplatform.web;

import com.farmplatform.dto.Dtos.*;
import com.farmplatform.security.AuthUser;
import com.farmplatform.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/register/farmer")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerFarmer(@Valid @RequestBody RegisterFarmerRequest req) {
        return auth.registerFarmer(req);
    }

    @PostMapping("/register/customer")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerCustomer(@Valid @RequestBody RegisterCustomerRequest req) {
        return auth.registerCustomer(req);
    }

    @GetMapping("/me")
    public AuthResponse me(@AuthenticationPrincipal AuthUser me) {
        return auth.me(me);
    }
}
