package com.farmplatform.service;

import com.farmplatform.common.ApiException;
import com.farmplatform.entity.FarmerPayment;
import com.farmplatform.payfast.Payfastclient;
import com.farmplatform.repository.Repos.FarmerPaymentRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Pays a farmer payment through the PayFast hosted page (redirect + notify), like the customer flow. */
@Service
public class FarmerPayfastService {
    public static final String PREFIX = "FARM-";

    private final Payfastclient payfast;
    private final FarmerPaymentRepo farmerPayments;
    private final SupplyService supply;

    public FarmerPayfastService(Payfastclient payfast, FarmerPaymentRepo farmerPayments, SupplyService supply) {
        this.payfast = payfast;
        this.farmerPayments = farmerPayments;
        this.supply = supply;
    }

    /** Returns the PayFast URL the admin must be sent to. */
    @Transactional(readOnly = true)
    public String start(Long farmerPaymentId) {
        if (payfast.isMock()) {
            throw ApiException.badRequest("PayFast is not enabled");
        }
        FarmerPayment fp = farmerPayments.findById(farmerPaymentId)
                .orElseThrow(() -> ApiException.notFound("Farmer payment not found"));
        if (!"PENDING".equals(fp.status)) {
            throw ApiException.conflict("This payment has already been completed");
        }
        String reference = PREFIX + fp.id + "-" + System.currentTimeMillis();
        return payfast.buildPaymentUrl(reference, fp.amount, "Farmer payment for PO #" + fp.purchaseOrder.id);
    }

    /** Called when PayFast notifies us about a payment whose reference starts with FARM-. */
    @Transactional
    public void handleNotification(String rawBody) {
        if (!payfast.verifySignature(rawBody)) {
            throw ApiException.badRequest("Invalid notification");
        }
        Map<String, String> p = Payfastclient.parse(rawBody);
        String reference = p.get("m_payment_id");
        Long id;
        try {
            id = Long.parseLong(reference.split("-")[1]);
        } catch (Exception e) {
            throw ApiException.badRequest("Unknown reference");
        }
        FarmerPayment fp = farmerPayments.findById(id).orElseThrow(() -> ApiException.notFound("Farmer payment not found"));
        if ("PAID".equals(fp.status)) {
            return; // PayFast may notify more than once
        }
        if (!payfast.confirmWithPayfast(rawBody)) {
            throw ApiException.badRequest("Notification not confirmed");
        }
        if (Payfastclient.isComplete(p) && Payfastclient.amountMatches(p, fp.amount)) {
            String pf = p.get("pf_payment_id");
            supply.markFarmerPaymentPaidOnline(fp.id, pf != null ? "PF-" + pf : reference);
        }
    }
}