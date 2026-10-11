package com.farmplatform.web;

import com.farmplatform.common.ApiException;
import com.farmplatform.dto.Dtos.*;
import com.farmplatform.dto.Views.AdminOrderView;
import com.farmplatform.entity.*;
import com.farmplatform.repository.Repos.*;
import com.farmplatform.service.DeliveryService;
import com.farmplatform.service.OrderService;
import com.farmplatform.service.ProcessingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Administrator: processing, inventory, customer demand, orders, deliveries, invoices and reports. */
@RestController
@RequestMapping("/api/admin")
public class AdminOperationsController {
    private static final Set<String> REQUIREMENT_STATUSES = Set.of("OPEN", "REVIEWED", "SOURCING", "FULFILLED", "CLOSED");

    private final ProcessingService processingService;
    private final ProcessingBatchRepo processing;
    private final InventoryRepo inventory;
    private final CustomerRequirementRepo requirements;
    private final CustomerOrderRepo orders;
    private final PaymentRepo payments;
    private final DeliveryRepo deliveries;
    private final DeliveryService deliveryService;
    private final InvoiceRepo invoices;
    private final OrderService orderService;
    private final FarmerRepo farmers;
    private final CustomerRepo customers;
    private final PurchaseOrderRepo purchaseOrders;
    private final FarmerPaymentRepo farmerPayments;

    public AdminOperationsController(ProcessingService processingService, ProcessingBatchRepo processing,
                                     InventoryRepo inventory, CustomerRequirementRepo requirements,
                                     CustomerOrderRepo orders, PaymentRepo payments, DeliveryRepo deliveries,
                                     DeliveryService deliveryService, InvoiceRepo invoices, OrderService orderService,
                                     FarmerRepo farmers, CustomerRepo customers, PurchaseOrderRepo purchaseOrders,
                                     FarmerPaymentRepo farmerPayments) {
        this.processingService = processingService;
        this.processing = processing;
        this.inventory = inventory;
        this.requirements = requirements;
        this.orders = orders;
        this.payments = payments;
        this.deliveries = deliveries;
        this.deliveryService = deliveryService;
        this.invoices = invoices;
        this.orderService = orderService;
        this.farmers = farmers;
        this.customers = customers;
        this.purchaseOrders = purchaseOrders;
        this.farmerPayments = farmerPayments;
    }

    // ---------- processing ----------

    @GetMapping("/processing")
    public List<ProcessingBatch> processing() {
        return processing.findAllByOrderByIdDesc();
    }

    @PostMapping("/processing")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcessingBatch startProcessing(@Valid @RequestBody ProcessingRequest req) {
        return processingService.create(req);
    }

    @PostMapping("/processing/{id}/complete")
    public ProcessingBatch completeProcessing(@PathVariable Long id, @RequestBody(required = false) CompleteProcessingRequest req) {
        return processingService.complete(id, req);
    }

    // ---------- inventory ----------

    @GetMapping("/inventory")
    public List<Inventory> inventory() {
        return inventory.findAllByOrderByIdDesc();
    }

    @PostMapping("/inventory")
    @ResponseStatus(HttpStatus.CREATED)
    public Inventory addInventory(@Valid @RequestBody InventoryRequest req) {
        return processingService.addToInventory(req);
    }

    @PutMapping("/inventory/{id}")
    public Inventory updateInventory(@PathVariable Long id, @RequestBody InventoryUpdateRequest req) {
        return processingService.updateInventory(id, req);
    }

    // ---------- customer demand ----------

    @GetMapping("/requirements")
    public List<AdminRequirementView> requirements() {
        return orderService.adminRequirements();
    }

    @PutMapping("/requirements/{id}/status")
    public CustomerRequirement setRequirementStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        String s = req.status().trim().toUpperCase();
        if (!REQUIREMENT_STATUSES.contains(s)) {
            throw ApiException.badRequest("status must be one of " + REQUIREMENT_STATUSES);
        }
        CustomerRequirement r = requirements.findById(id).orElseThrow(() -> ApiException.notFound("Requirement not found"));
        r.status = s;
        return requirements.save(r);
    }

    // ---------- orders, payments ----------

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public List<AdminOrderView> orders() {
        return orders.findAllByOrderByIdDesc().stream().map(this::adminView).toList();
    }

    @GetMapping("/orders/{id}")
    @Transactional(readOnly = true)
    public AdminOrderView order(@PathVariable Long id) {
        return adminView(orders.findById(id).orElseThrow(() -> ApiException.notFound("Order not found")));
    }

    @GetMapping("/payments")
    public List<Payment> payments() {
        return payments.findAllByOrderByIdDesc();
    }

    // ---------- deliveries ----------

    @GetMapping("/deliveries")
    public List<Delivery> deliveries() {
        return deliveries.findAllByOrderByIdDesc();
    }

    @PostMapping("/deliveries")
    @ResponseStatus(HttpStatus.CREATED)
    public Delivery scheduleDelivery(@Valid @RequestBody DeliveryRequest req) {
        return deliveryService.schedule(req);
    }

    // ---------- invoices ----------

    @GetMapping("/invoices")
    public List<Invoice> invoices() {
        return invoices.findAllByOrderByIdDesc();
    }

    // ---------- dashboard / reports ----------

    @GetMapping("/reports/summary")
    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();

        Map<String, Long> farmersByStatus = farmers.findAll().stream()
                .collect(Collectors.groupingBy(f -> f.verificationStatus, TreeMap::new, Collectors.counting()));
        m.put("farmersByStatus", farmersByStatus);
        m.put("customerCount", customers.count());

        List<CustomerOrder> allOrders = orders.findAll();
        Map<String, Long> ordersByStatus = allOrders.stream()
                .collect(Collectors.groupingBy(o -> o.status, TreeMap::new, Collectors.counting()));
        m.put("ordersByStatus", ordersByStatus);
        m.put("ordersAwaitingDelivery", allOrders.stream().filter(o -> "PAID".equals(o.status)).count());

        m.put("revenueReceived", payments.sumCompleted());
        m.put("farmerPaymentsMade", farmerPayments.sumPaid());
        m.put("farmerPaymentsPending", farmerPayments.findAll().stream().filter(p -> "PENDING".equals(p.status)).count());

        m.put("purchaseOrdersAwaitingFarmer", purchaseOrders.findAll().stream().filter(p -> "PENDING".equals(p.status)).count());
        m.put("openRequirements", requirements.findAll().stream().filter(r -> "OPEN".equals(r.status)).count());

        List<Inventory> stock = inventory.findAll();
        BigDecimal stockValue = stock.stream()
                .filter(i -> "AVAILABLE".equals(i.status) && i.quantity != null && i.sellingPrice != null)
                .map(i -> i.quantity.multiply(i.sellingPrice))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("stockValueAtSellingPrice", stockValue);
        m.put("inventoryLines", stock.stream().filter(i -> "AVAILABLE".equals(i.status)).count());
        return m;
    }

    private AdminOrderView adminView(CustomerOrder o) {
        String payStatus = payments.findByOrderId(o.id).stream().findFirst().map(p -> p.paymentStatus).orElse(null);
        return new AdminOrderView(o, orderService.itemViews(o.id), payStatus);
    }
}
