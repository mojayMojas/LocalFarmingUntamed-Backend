package com.farmplatform.web;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.farmplatform.service.FarmerPayfastService;

/** Administrator: pay a pending farmer payment through the PayFast hosted page. */
@RestController
@RequestMapping("/api/admin/farmer-payments")
public class AdminFarmerPayfastController {
    private final FarmerPayfastService service;

    public AdminFarmerPayfastController(FarmerPayfastService service) {
        this.service = service;
    }

    @PostMapping("/{id}/pay-online")
    public Map<String, String> payOnline(@PathVariable Long id) {
        return Map.of("redirectUrl", service.start(id));
    }
}