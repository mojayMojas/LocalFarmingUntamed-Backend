package com.farmplatform.service;

import com.farmplatform.common.ApiException;
import com.farmplatform.dto.Dtos.*;
import com.farmplatform.entity.*;
import com.farmplatform.repository.Repos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Customer side: catalogue, requirements, orders. Nothing returned from here identifies a farmer. */
@Service
public class OrderService {
    private final InventoryRepo inventory;
    private final ProductRepo products;
    private final CustomerRequirementRepo requirements;
    private final CustomerOrderRepo orders;
    private final CustomerOrderItemRepo orderItems;
    private final InvoiceRepo invoices;
    private final PaymentRepo payments;
    private final DeliveryRepo deliveries;
    private final NotificationService notifications;

    public OrderService(InventoryRepo inventory, ProductRepo products, CustomerRequirementRepo requirements,
                        CustomerOrderRepo orders, CustomerOrderItemRepo orderItems, InvoiceRepo invoices,
                        PaymentRepo payments, DeliveryRepo deliveries, NotificationService notifications) {
        this.inventory = inventory;
        this.products = products;
        this.requirements = requirements;
        this.orders = orders;
        this.orderItems = orderItems;
        this.invoices = invoices;
        this.payments = payments;
        this.deliveries = deliveries;
        this.notifications = notifications;
    }

    // ---------- catalogue & requirements ----------

    @Transactional(readOnly = true)
    public List<CatalogItem> catalog() {
        return inventory.findByStatusAndQuantityGreaterThan("AVAILABLE", BigDecimal.ZERO).stream()
                .filter(i -> i.sellingPrice != null)
                .map(i -> new CatalogItem(i.id, i.product.id, i.product.productName, i.product.category.categoryName,
                        unitOf(i), i.quantity, i.sellingPrice))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal availableStock(Long productId) {
        return inventory.findByStatusAndQuantityGreaterThan("AVAILABLE", BigDecimal.ZERO).stream()
                .filter(i -> i.product.id.equals(productId))
                .map(i -> i.quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(Long productId, BigDecimal requested) {
        Product product = products.findById(productId).orElseThrow(() -> ApiException.notFound("Product not found"));
        BigDecimal available = availableStock(productId);
        BigDecimal shortfall = requested.subtract(available).max(BigDecimal.ZERO);
        return new AvailabilityResponse(product.id, product.productName, product.unit, requested, available,
                shortfall.signum() == 0, shortfall);
    }

    @Transactional
    public CustomerRequirement createRequirement(Customer customer, RequirementRequest req) {
        Product product = products.findById(req.productId()).orElseThrow(() -> ApiException.notFound("Product not found"));
        CustomerRequirement r = new CustomerRequirement();
        r.customer = customer;
        r.product = product;
        r.quantity = req.quantity();
        r.unit = (req.unit() != null && !req.unit().isBlank()) ? req.unit() : product.unit;
        r.requiredDate = req.requiredDate();
        r.specifications = req.specifications();
        r.status = "OPEN";
        r = requirements.save(r);
        notifications.notifyAdmins("New customer requirement",
                customer.companyName + " needs " + req.quantity().toPlainString() + " " + (r.unit == null ? "" : r.unit)
                        + " of " + product.productName + ".", "ORDER");
        return r;
    }

    @Transactional(readOnly = true)
    public List<AdminRequirementView> adminRequirements() {
        return requirements.findAllByOrderByIdDesc().stream().map(r -> {
            BigDecimal stock = availableStock(r.product.id);
            return new AdminRequirementView(r, stock, r.quantity.subtract(stock).max(BigDecimal.ZERO));
        }).toList();
    }

    // ---------- orders ----------

    @Transactional
    public OrderView placeOrder(Customer customer, PlaceOrderRequest req) {
        if (!"ACTIVE".equals(customer.status)) {
            throw ApiException.forbidden("Your customer account is suspended. Please contact the platform owner.");
        }
        // Merge duplicate lines and lock rows in a stable order (TreeMap) to avoid deadlocks.
        Map<Long, BigDecimal> wanted = new TreeMap<>();
        for (OrderItemRequest it : req.items()) {
            wanted.merge(it.inventoryId(), it.quantity(), BigDecimal::add);
        }

        CustomerOrder order = new CustomerOrder();
        order.customer = customer;
        order = orders.save(order);

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : wanted.entrySet()) {
            Inventory inv = inventory.lockById(e.getKey()).orElseThrow(() -> ApiException.notFound("Product no longer listed"));
            String name = inv.product.productName;
            if (!"AVAILABLE".equals(inv.status) || inv.quantity == null || inv.sellingPrice == null) {
                throw ApiException.conflict(name + " is no longer available");
            }
            if (inv.quantity.compareTo(e.getValue()) < 0) {
                throw ApiException.conflict("Only " + inv.quantity.stripTrailingZeros().toPlainString() + " " + unitOf(inv)
                        + " of " + name + " is available");
            }
            inv.quantity = inv.quantity.subtract(e.getValue());
            if (inv.quantity.signum() == 0) {
                inv.status = "OUT_OF_STOCK";
            }
            inv.updatedAt = LocalDateTime.now();
            inventory.save(inv);

            CustomerOrderItem item = new CustomerOrderItem();
            item.order = order;
            item.product = inv.product;
            item.inventory = inv;
            item.quantity = e.getValue();
            item.unitPrice = inv.sellingPrice;
            item.subtotal = e.getValue().multiply(inv.sellingPrice).setScale(2, RoundingMode.HALF_UP);
            total = total.add(item.subtotal);
            orderItems.save(item);
        }
        order.totalAmount = total;
        order.status = "PENDING_PAYMENT";
        order = orders.save(order);

        Invoice invoice = new Invoice();
        invoice.order = order;
        invoice.invoiceNumber = "INV-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%05d", order.id);
        invoice.amount = total;
        invoice.issueDate = LocalDate.now();
        invoice.dueDate = LocalDate.now().plusDays(7);
        invoice.status = "UNPAID";
        invoices.save(invoice);

        notifications.notify(customer.user, "Order placed",
                "Order #" + order.id + " was placed. Please complete the payment to confirm it.", "ORDER");
        notifications.notifyAdmins("New order #" + order.id, customer.companyName + " placed an order of R" + total.toPlainString() + ".", "ORDER");
        return view(order);
    }

    @Transactional
    public OrderView cancelOrder(Customer customer, Long orderId) {
        CustomerOrder order = ownedOrder(customer, orderId);
        if (!"PENDING_PAYMENT".equals(order.status)) {
            throw ApiException.conflict("Only unpaid orders can be cancelled");
        }
        for (CustomerOrderItem item : orderItems.findByOrderId(order.id)) {
            if (item.inventory == null) {
                continue;
            }
            Inventory inv = inventory.lockById(item.inventory.id).orElse(null);
            if (inv != null) {
                inv.quantity = (inv.quantity == null ? BigDecimal.ZERO : inv.quantity).add(item.quantity);
                if ("OUT_OF_STOCK".equals(inv.status)) {
                    inv.status = "AVAILABLE";
                }
                inv.updatedAt = LocalDateTime.now();
                inventory.save(inv);
            }
        }
        for (Payment p : payments.findByOrderId(order.id)) {
            if ("PENDING".equals(p.paymentStatus)) {
                p.paymentStatus = "CANCELLED";
                payments.save(p);
            }
        }
        invoices.findByOrderId(order.id).ifPresent(inv -> {
            inv.status = "CANCELLED";
            invoices.save(inv);
        });
        order.status = "CANCELLED";
        orders.save(order);
        notifications.notifyAdmins("Order #" + order.id + " cancelled", customer.companyName + " cancelled an unpaid order.", "ORDER");
        return view(order);
    }

    @Transactional(readOnly = true)
    public List<OrderView> myOrders(Customer customer) {
        return orders.findByCustomerIdOrderByIdDesc(customer.id).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public OrderView myOrder(Customer customer, Long orderId) {
        return view(ownedOrder(customer, orderId));
    }

    public CustomerOrder ownedOrder(Customer customer, Long orderId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.customer.id.equals(customer.id)) {
            throw ApiException.notFound("Order not found"); // never reveal other customers' orders
        }
        return order;
    }

    /** Customer-safe projection of an order (no batch, farmer or purchase-price data). */
    public OrderView view(CustomerOrder order) {
        List<OrderItemView> items = itemViews(order.id);
        Invoice invoice = invoices.findByOrderId(order.id).orElse(null);
        String payStatus = payments.findByOrderId(order.id).stream().findFirst().map(p -> p.paymentStatus).orElse(null);
        Delivery d = deliveries.findByOrderId(order.id).stream().findFirst().orElse(null);
        DeliveryCustomerView dv = d == null ? null : new DeliveryCustomerView(d.id, d.status, d.scheduledDate,
                d.deliveryDate, d.deliveryAddress, d.driver.firstName + " " + d.driver.lastName, d.driver.phone,
                d.driver.vehicleType, d.driver.vehicleRegistration);
        return new OrderView(order.id, order.orderDate, order.status, order.totalAmount, items,
                invoice == null ? null : invoice.id, invoice == null ? null : invoice.invoiceNumber,
                invoice == null ? null : invoice.status, payStatus, dv);
    }

    public List<OrderItemView> itemViews(Long orderId) {
        return orderItems.findByOrderId(orderId).stream()
                .map(i -> new OrderItemView(i.product.id, i.product.productName,
                        i.inventory != null ? unitOf(i.inventory) : i.product.unit,
                        i.quantity, i.unitPrice, i.subtotal))
                .toList();
    }

    private static String unitOf(Inventory i) {
        return i.unit != null ? i.unit : i.product.unit;
    }
}
