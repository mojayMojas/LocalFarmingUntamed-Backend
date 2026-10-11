package com.farmplatform.service;

import com.farmplatform.common.ApiException;
import com.farmplatform.dto.Dtos.PaymentInitResponse;
import com.farmplatform.dto.Dtos.PaymentStatusView;
import com.farmplatform.entity.Customer;
import com.farmplatform.entity.CustomerOrder;
import com.farmplatform.entity.Payment;
import com.farmplatform.payfast.Payfastclient;
import com.farmplatform.repository.Repos.CustomerOrderRepo;
import com.farmplatform.repository.Repos.InvoiceRepo;
import com.farmplatform.repository.Repos.PaymentRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepo payments;
    private final CustomerOrderRepo orders;
    private final InvoiceRepo invoices;
    private final Payfastclient payfast;
    private final OrderService orderService;
    private final NotificationService notifications;
    private final String frontendUrl;

    public PaymentService(PaymentRepo payments, CustomerOrderRepo orders, InvoiceRepo invoices, Payfastclient payfast,
                          OrderService orderService, NotificationService notifications,
                          @Value("${app.frontend-url}") String frontendUrl) {
        this.payments = payments;
        this.orders = orders;
        this.invoices = invoices;
        this.payfast = payfast;
        this.orderService = orderService;
        this.notifications = notifications;
        this.frontendUrl = frontendUrl;
    }

    /** Starts a PayFast payment for an unpaid order and returns the URL the customer must be sent to. */
    @Transactional
    public PaymentInitResponse initiate(Customer customer, Long orderId) {
        CustomerOrder order = orderService.ownedOrder(customer, orderId);
        if (!"PENDING_PAYMENT".equals(order.status)) {
            throw ApiException.conflict("This order is not waiting for payment");
        }
        for (Payment old : payments.findByOrderId(order.id)) {
            if ("PENDING".equals(old.paymentStatus)) {
                old.paymentStatus = "CANCELLED";
                payments.save(old);
            }
        }

        boolean mock = payfast.isMock();
        Payment payment = new Payment();
        payment.order = order;
        payment.ozowReference = "FP-" + order.id + "-" + System.currentTimeMillis(); // column name kept; holds our reference
        payment.amount = order.totalAmount;
        payment.paymentMethod = mock ? "MOCK" : "PAYFAST";
        payment.paymentStatus = "PENDING";
        payment = payments.save(payment);

        String redirectUrl = mock
                ? frontendUrl + "/payment/mock?reference=" + payment.ozowReference
                : payfast.buildPaymentUrl(payment.ozowReference, order.totalAmount, "FarmLink order #" + order.id);
        return new PaymentInitResponse(payment.id, payment.ozowReference, redirectUrl, mock);
    }

    /** Kept only so the old Ozow controller still compiles. Ozow is no longer used. */
    @Transactional
    public void handleNotification(Map<String, String> raw) {
        throw ApiException.badRequest("Ozow is not enabled");
    }

    /** Called by PayFast server-to-server (notify_url). Throws on anything that must not be trusted. */
    @Transactional
    public void handlePayfastNotification(String rawBody) {
        if (payfast.isMock()) {
            throw ApiException.badRequest("PayFast is not enabled");
        }
        if (!payfast.verifySignature(rawBody)) {
            log.warn("Rejected PayFast notification with an invalid signature");
            throw ApiException.badRequest("Invalid notification");
        }
        Map<String, String> p = Payfastclient.parse(rawBody);
        String reference = p.get("m_payment_id");
        Payment payment = payments.findByOzowReference(reference)
                .orElseThrow(() -> ApiException.notFound("Unknown payment reference"));
        if ("COMPLETE".equals(payment.paymentStatus)) {
            return; // PayFast may notify more than once
        }
        if (!payfast.confirmWithPayfast(rawBody)) {
            log.warn("PayFast did not confirm notification for {}", reference);
            throw ApiException.badRequest("Notification not confirmed");
        }
        if (p.get("pf_payment_id") != null) {
            payment.ozowTransactionId = p.get("pf_payment_id");
        }

        String status = p.getOrDefault("payment_status", "").trim().toUpperCase();
        switch (status) {
            case "COMPLETE" -> {
                if (!Payfastclient.amountMatches(p, payment.amount)) {
                    log.error("Amount mismatch for {}: expected {}, PayFast reported {}", reference, payment.amount, p.get("amount_gross"));
                    payment.paymentStatus = "FAILED";
                    payments.save(payment);
                    notifications.notifyAdmins("Payment amount mismatch",
                            "PayFast reported a different amount for reference " + reference + ". Check it manually.", "PAYMENT");
                    return;
                }
                markPaid(payment);
            }
            case "CANCELLED" -> markUnsuccessful(payment, "CANCELLED");
            case "FAILED" -> markUnsuccessful(payment, "FAILED");
            default -> payments.save(payment);
        }
    }

    /** Development-only: finishes a fake payment when mock mode is on. */
    @Transactional
    public PaymentStatusView completeMock(Customer customer, String reference, boolean success) {
        if (!payfast.isMock()) {
            throw ApiException.forbidden("Mock payments are disabled");
        }
        Payment payment = payments.findByOzowReference(reference).orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (!payment.order.customer.id.equals(customer.id)) {
            throw ApiException.notFound("Payment not found");
        }
        if (!"PENDING".equals(payment.paymentStatus)) {
            throw ApiException.conflict("This payment has already been finalised");
        }
        if (success) {
            markPaid(payment);
        } else {
            markUnsuccessful(payment, "CANCELLED");
        }
        return status(customer, reference);
    }

    @Transactional(readOnly = true)
    public PaymentStatusView status(Customer customer, String reference) {
        Payment payment = payments.findByOzowReference(reference).orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (!payment.order.customer.id.equals(customer.id)) {
            throw ApiException.notFound("Payment not found");
        }
        return new PaymentStatusView(payment.ozowReference, payment.paymentStatus, payment.order.id, payment.order.status);
    }

    private void markPaid(Payment payment) {
        payment.paymentStatus = "COMPLETE";
        payment.paidAt = LocalDateTime.now();
        payments.save(payment);

        CustomerOrder order = payment.order;
        if ("PENDING_PAYMENT".equals(order.status)) {
            order.status = "PAID";
            orders.save(order);
            invoices.findByOrderId(order.id).ifPresent(inv -> {
                inv.status = "PAID";
                invoices.save(inv);
            });
            notifications.notify(order.customer.user, "Payment received",
                    "We received your payment of R" + payment.amount.toPlainString() + " for order #" + order.id + ".", "PAYMENT");
            notifications.notifyAdmins("Order #" + order.id + " paid",
                    "Payment of R" + payment.amount.toPlainString() + " was confirmed. Please schedule the delivery.", "PAYMENT");
        } else {
            log.error("Payment {} completed for order {} which is {}", payment.ozowReference, order.id, order.status);
            notifications.notifyAdmins("Payment for a cancelled order",
                    "Payment " + payment.ozowReference + " completed but order #" + order.id + " is " + order.status
                            + ". A refund is probably required.", "PAYMENT");
        }
    }

    private void markUnsuccessful(Payment payment, String newStatus) {
        payment.paymentStatus = newStatus;
        payments.save(payment);
        notifications.notify(payment.order.customer.user, "Payment not completed",
                "The payment for order #" + payment.order.id + " was not completed. You can try again from your orders.", "PAYMENT");
    }
}