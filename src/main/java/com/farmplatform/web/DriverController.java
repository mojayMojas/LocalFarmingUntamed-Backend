package com.farmplatform.web;

import com.farmplatform.dto.Dtos.DeliveryDriverView;
import com.farmplatform.dto.Dtos.StatusRequest;
import com.farmplatform.security.AuthUser;
import com.farmplatform.service.CurrentActors;
import com.farmplatform.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/driver")
public class DriverController {
    private final CurrentActors actors;
    private final DeliveryService deliveries;

    public DriverController(CurrentActors actors, DeliveryService deliveries) {
        this.actors = actors;
        this.deliveries = deliveries;
    }

    @GetMapping("/deliveries")
    public List<DeliveryDriverView> myDeliveries(@AuthenticationPrincipal AuthUser me) {
        return deliveries.forDriver(actors.driver(me));
    }

    /** status: IN_TRANSIT, DELIVERED or FAILED */
    @PostMapping("/deliveries/{id}/status")
    public DeliveryDriverView updateStatus(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                           @Valid @RequestBody StatusRequest req) {
        return deliveries.updateStatus(actors.driver(me), id, req.status());
    }
}
