package com.farmplatform.service;

import com.farmplatform.common.ApiException;
import com.farmplatform.dto.Dtos.*;
import com.farmplatform.entity.CustomerOrder;
import com.farmplatform.entity.Delivery;
import com.farmplatform.entity.Driver;
import com.farmplatform.repository.Repos.CustomerOrderRepo;
import com.farmplatform.repository.Repos.DeliveryRepo;
import com.farmplatform.repository.Repos.DriverRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class DeliveryService {
    private static final Set<String> ACTIVE = Set.of("SCHEDULED", "IN_TRANSIT");

    private final DeliveryRepo deliveries;
    private final CustomerOrderRepo orders;
    private final DriverRepo drivers;
    private final OrderService orderService;
    private final NotificationService notifications;

    public DeliveryService(DeliveryRepo deliveries, CustomerOrderRepo orders, DriverRepo drivers,
                           OrderService orderService, NotificationService notifications) {
        this.deliveries = deliveries;
        this.orders = orders;
        this.drivers = drivers;
        this.orderService = orderService;
        this.notifications = notifications;
    }

    @Transactional
    public Delivery schedule(DeliveryRequest req) {
        CustomerOrder order = orders.findById(req.orderId()).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!"PAID".equals(order.status)) {
            throw ApiException.conflict("Only paid orders waiting for delivery can be scheduled");
        }
        boolean hasActive = deliveries.findByOrderId(order.id).stream().anyMatch(d -> ACTIVE.contains(d.status));
        if (hasActive) {
            throw ApiException.conflict("This order already has an active delivery");
        }
        Driver driver = drivers.findById(req.driverId()).orElseThrow(() -> ApiException.notFound("Driver not found"));
        if (!"ACTIVE".equals(driver.status)) {
            throw ApiException.badRequest("That driver is not active");
        }
        Delivery d = new Delivery();
        d.order = order;
        d.driver = driver;
        d.deliveryAddress = (req.deliveryAddress() != null && !req.deliveryAddress().isBlank())
                ? req.deliveryAddress() : order.customer.address;
        d.scheduledDate = req.scheduledDate();
        d.status = "SCHEDULED";
        d = deliveries.save(d);

        order.status = "SCHEDULED";
        orders.save(order);
        notifications.notify(order.customer.user, "Delivery scheduled",
                "Order #" + order.id + " will be delivered on " + req.scheduledDate() + ".", "DELIVERY");
        notifications.notify(driver.user, "New delivery assigned",
                "You have been assigned the delivery for order #" + order.id + " on " + req.scheduledDate() + ".", "DELIVERY");
        return d;
    }

    @Transactional
    public DeliveryDriverView updateStatus(Driver driver, Long deliveryId, String newStatus) {
        Delivery d = deliveries.findById(deliveryId).orElseThrow(() -> ApiException.notFound("Delivery not found"));
        if (!d.driver.id.equals(driver.id)) {
            throw ApiException.notFound("Delivery not found");
        }
        String target = newStatus.trim().toUpperCase();
        boolean allowed = switch (d.status) {
            case "SCHEDULED" -> target.equals("IN_TRANSIT") || target.equals("FAILED");
            case "IN_TRANSIT" -> target.equals("DELIVERED") || target.equals("FAILED");
            default -> false;
        };
        if (!allowed) {
            throw ApiException.conflict("A delivery that is " + d.status + " cannot be changed to " + target);
        }
        CustomerOrder order = d.order;
        d.status = target;
        switch (target) {
            case "IN_TRANSIT" -> {
                order.status = "OUT_FOR_DELIVERY";
                notifications.notify(order.customer.user, "Order on its way",
                        "Order #" + order.id + " is out for delivery.", "DELIVERY");
            }
            case "DELIVERED" -> {
                d.deliveryDate = LocalDate.now();
                order.status = "DELIVERED";
                notifications.notify(order.customer.user, "Order delivered",
                        "Order #" + order.id + " has been delivered.", "DELIVERY");
                notifications.notifyAdmins("Order #" + order.id + " delivered", "The driver confirmed the delivery.", "DELIVERY");
            }
            default -> {
                order.status = "PAID"; // back in the queue so the admin can reschedule
                notifications.notifyAdmins("Delivery failed for order #" + order.id,
                        "The driver could not complete the delivery. Please reschedule.", "DELIVERY");
            }
        }
        deliveries.save(d);
        orders.save(order);
        return driverView(d);
    }

    @Transactional(readOnly = true)
    public List<DeliveryDriverView> forDriver(Driver driver) {
        return deliveries.findByDriverId(driver.id).stream().map(this::driverView).toList();
    }

    public DeliveryDriverView driverView(Delivery d) {
        CustomerOrder o = d.order;
        return new DeliveryDriverView(d.id, o.id, d.status, d.scheduledDate, d.deliveryDate, d.deliveryAddress,
                o.customer.companyName, o.customer.user.firstName + " " + o.customer.user.lastName,
                o.customer.user.phone, orderService.itemViews(o.id));
    }
}
