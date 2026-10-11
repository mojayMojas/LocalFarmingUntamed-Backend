package com.farmplatform.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.farmplatform.payfast.Payfastclient;
import com.farmplatform.service.FarmerPayfastService;
import com.farmplatform.service.PaymentService;

/** Receives PayFast's server-to-server payment notification (ITN). Must be reachable without a login. */
@RestController
@RequestMapping("/api/payments/payfast")
public class PayfastNotifyController {
    private final PaymentService payments;
    private final FarmerPayfastService farmerPayfast;

    public PayfastNotifyController(PaymentService payments, FarmerPayfastService farmerPayfast) {
        this.payments = payments;
        this.farmerPayfast = farmerPayfast;
    }

    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> notify(@RequestBody String rawBody) {
        String ref = Payfastclient.parse(rawBody).get("m_payment_id");
        if (ref != null && ref.startsWith(FarmerPayfastService.PREFIX)) {
            farmerPayfast.handleNotification(rawBody);   // payment to a farmer
        } else {
            payments.handlePayfastNotification(rawBody); // payment from a customer
        }
        return ResponseEntity.ok().build();
    }
}