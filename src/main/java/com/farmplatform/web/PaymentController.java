package com.farmplatform.web;

import com.farmplatform.service.PaymentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Public endpoint Ozow calls (server to server) once a payment finishes. Trust comes from the hash check. */
@RestController
@RequestMapping("/api/payments/ozow")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String notifyForm(@RequestParam Map<String, String> params) {
        payments.handleNotification(params);
        return "OK";
    }

    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String notifyJson(@RequestBody Map<String, Object> body) {
        Map<String, String> params = new LinkedHashMap<>();
        body.forEach((k, v) -> params.put(k, v == null ? null : String.valueOf(v)));
        payments.handleNotification(params);
        return "OK";
    }
}
