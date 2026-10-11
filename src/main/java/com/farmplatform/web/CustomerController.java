package com.farmplatform.web;

import com.farmplatform.common.ApiException;
import com.farmplatform.dto.Dtos.*;
import com.farmplatform.entity.Customer;
import com.farmplatform.entity.CustomerRequirement;
import com.farmplatform.entity.Invoice;
import com.farmplatform.entity.User;
import com.farmplatform.repository.Repos.*;
import com.farmplatform.security.AuthUser;
import com.farmplatform.service.AuthService;
import com.farmplatform.service.CurrentActors;
import com.farmplatform.service.OrderService;
import com.farmplatform.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything a bulk customer can see or do. Responses never contain farmer or purchase-price information. */
@RestController
@RequestMapping("/api/customer")
public class CustomerController {
    private final CurrentActors actors;
    private final AuthService auth;
    private final UserRepo users;
    private final CustomerRepo customers;
    private final CustomerRequirementRepo requirements;
    private final InvoiceRepo invoices;
    private final OrderService orders;
    private final PaymentService payments;

    public CustomerController(CurrentActors actors, AuthService auth, UserRepo users, CustomerRepo customers,
                              CustomerRequirementRepo requirements, InvoiceRepo invoices, OrderService orders,
                              PaymentService payments) {
        this.actors = actors;
        this.auth = auth;
        this.users = users;
        this.customers = customers;
        this.requirements = requirements;
        this.invoices = invoices;
        this.orders = orders;
        this.payments = payments;
    }

    // ---------- profile ----------

    @GetMapping("/profile")
    public Map<String, Object> profile(@AuthenticationPrincipal AuthUser me) {
        Customer c = actors.customer(me);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", auth.me(me));
        m.put("companyName", c.companyName);
        m.put("customerType", c.customerType);
        m.put("businessRegistration", c.businessRegistration);
        m.put("address", c.address);
        m.put("status", c.status);
        return m;
    }

    @PutMapping("/profile")
    @Transactional
    public Map<String, Object> updateProfile(@AuthenticationPrincipal AuthUser me, @RequestBody CustomerProfileUpdateRequest req) {
        Customer c = actors.customer(me);
        User u = actors.user(me);
        if (req.firstName() != null && !req.firstName().isBlank()) u.firstName = req.firstName().trim();
        if (req.lastName() != null && !req.lastName().isBlank()) u.lastName = req.lastName().trim();
        if (req.phone() != null) u.phone = req.phone();
        if (req.companyName() != null && !req.companyName().isBlank()) c.companyName = req.companyName().trim();
        if (req.customerType() != null) c.customerType = req.customerType();
        if (req.businessRegistration() != null) c.businessRegistration = req.businessRegistration();
        if (req.address() != null && !req.address().isBlank()) c.address = req.address().trim();
        users.save(u);
        customers.save(c);
        return profile(me);
    }

    // ---------- catalogue & requirements ----------

    @GetMapping("/catalog")
    public List<CatalogItem> catalog() {
        return orders.catalog();
    }

    @GetMapping("/availability")
    public AvailabilityResponse availability(@RequestParam Long productId, @RequestParam BigDecimal quantity) {
        if (quantity.signum() <= 0) {
            throw ApiException.badRequest("quantity must be above 0");
        }
        return orders.availability(productId, quantity);
    }

    @GetMapping("/requirements")
    public List<CustomerRequirement> myRequirements(@AuthenticationPrincipal AuthUser me) {
        return requirements.findByCustomerIdOrderByIdDesc(actors.customer(me).id);
    }

    @PostMapping("/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerRequirement createRequirement(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody RequirementRequest req) {
        return orders.createRequirement(actors.customer(me), req);
    }

    // ---------- orders & payment ----------

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView placeOrder(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PlaceOrderRequest req) {
        return orders.placeOrder(actors.customer(me), req);
    }

    @GetMapping("/orders")
    public List<OrderView> myOrders(@AuthenticationPrincipal AuthUser me) {
        return orders.myOrders(actors.customer(me));
    }

    @GetMapping("/orders/{id}")
    public OrderView myOrder(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return orders.myOrder(actors.customer(me), id);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderView cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return orders.cancelOrder(actors.customer(me), id);
    }

    /** Starts the Ozow payment; the response contains the URL to send the browser to. */
    @PostMapping("/orders/{id}/pay")
    public PaymentInitResponse pay(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return payments.initiate(actors.customer(me), id);
    }

    @GetMapping("/payments/status")
    public PaymentStatusView paymentStatus(@AuthenticationPrincipal AuthUser me, @RequestParam String reference) {
        return payments.status(actors.customer(me), reference);
    }

    /** Only works while Ozow credentials are blank (mock mode). */
    @PostMapping("/payments/mock/{reference}/complete")
    public PaymentStatusView completeMock(@AuthenticationPrincipal AuthUser me, @PathVariable String reference,
                                          @RequestParam(defaultValue = "true") boolean success) {
        return payments.completeMock(actors.customer(me), reference, success);
    }

    // ---------- invoices ----------

    @GetMapping("/invoices")
    public List<Invoice> myInvoices(@AuthenticationPrincipal AuthUser me) {
        return invoices.findByCustomerId(actors.customer(me).id);
    }

    @GetMapping("/invoices/{id}")
    public Map<String, Object> invoice(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Customer c = actors.customer(me);
        Invoice inv = invoices.findById(id).orElseThrow(() -> ApiException.notFound("Invoice not found"));
        if (!inv.order.customer.id.equals(c.id)) {
            throw ApiException.notFound("Invoice not found");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invoice", inv);
        m.put("order", orders.view(inv.order));
        m.put("billTo", Map.of("companyName", c.companyName, "address", c.address == null ? "" : c.address,
                "businessRegistration", c.businessRegistration == null ? "" : c.businessRegistration));
        return m;
    }
}
