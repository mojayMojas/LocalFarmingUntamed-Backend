package com.farmplatform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmplatform.common.ApiException;
import com.farmplatform.dto.Dtos.CreateFarmerPaymentRequest;
import com.farmplatform.dto.Dtos.CreatePurchaseOrderRequest;
import com.farmplatform.dto.Dtos.FarmerPaymentUpdateRequest;
import com.farmplatform.dto.Dtos.InspectionRequest;
import com.farmplatform.dto.Dtos.PoItemRequest;
import com.farmplatform.dto.Dtos.ReceiveRequest;
import com.farmplatform.entity.Farmer;
import com.farmplatform.entity.FarmerPayment;
import com.farmplatform.entity.Product;
import com.farmplatform.entity.PurchaseOrder;
import com.farmplatform.entity.PurchaseOrderItem;
import com.farmplatform.entity.QualityInspection;
import com.farmplatform.entity.ReceivingBatch;
import com.farmplatform.entity.User;
import com.farmplatform.repository.Repos.FarmerPaymentRepo;
import com.farmplatform.repository.Repos.FarmerRepo;
import com.farmplatform.repository.Repos.ProductRepo;
import com.farmplatform.repository.Repos.PurchaseOrderItemRepo;
import com.farmplatform.repository.Repos.PurchaseOrderRepo;
import com.farmplatform.repository.Repos.QualityInspectionRepo;
import com.farmplatform.repository.Repos.ReceivingBatchRepo;
import com.farmplatform.repository.Repos.UserRepo;
import com.farmplatform.security.AuthUser;

/** Farmer purchasing: purchase orders, receiving, quality inspection and paying farmers. */
@Service
public class SupplyService {
    private final FarmerRepo farmers;
    private final ProductRepo products;
    private final PurchaseOrderRepo purchaseOrders;
    private final PurchaseOrderItemRepo poItems;
    private final ReceivingBatchRepo batches;
    private final QualityInspectionRepo inspections;
    private final FarmerPaymentRepo farmerPayments;
    private final UserRepo users;
    private final NotificationService notifications;

    public SupplyService(FarmerRepo farmers, ProductRepo products, PurchaseOrderRepo purchaseOrders,
                         PurchaseOrderItemRepo poItems, ReceivingBatchRepo batches, QualityInspectionRepo inspections,
                         FarmerPaymentRepo farmerPayments, UserRepo users, NotificationService notifications) {
        this.farmers = farmers;
        this.products = products;
        this.purchaseOrders = purchaseOrders;
        this.poItems = poItems;
        this.batches = batches;
        this.inspections = inspections;
        this.farmerPayments = farmerPayments;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional
    public PurchaseOrder createPurchaseOrder(CreatePurchaseOrderRequest req) {
        Farmer farmer = farmers.findById(req.farmerId()).orElseThrow(() -> ApiException.notFound("Farmer not found"));
        if (!"VERIFIED".equals(farmer.verificationStatus)) {
            throw ApiException.badRequest("The farmer must be verified before a purchase order can be created");
        }
        PurchaseOrder po = new PurchaseOrder();
        po.farmer = farmer;
        po = purchaseOrders.save(po);

        BigDecimal total = BigDecimal.ZERO;
        for (PoItemRequest it : req.items()) {
            Product product = products.findById(it.productId())
                    .orElseThrow(() -> ApiException.notFound("Product " + it.productId() + " not found"));
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.purchaseOrder = po;
            item.product = product;
            item.quantity = it.quantity();
            item.unitPrice = it.unitPrice();
            item.subtotal = it.quantity().multiply(it.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            total = total.add(item.subtotal);
            poItems.save(item);
        }
        po.totalAmount = total;
        po = purchaseOrders.save(po);

        notifications.notify(farmer.user, "New supply request",
                "The platform has sent you purchase order #" + po.id + ". Please confirm or decline it.", "SUPPLY");
        return po;
    }

    @Transactional
    public PurchaseOrder farmerRespond(Farmer farmer, Long poId, boolean accept) {
        PurchaseOrder po = purchaseOrders.findById(poId).orElseThrow(() -> ApiException.notFound("Purchase order not found"));
        if (!po.farmer.id.equals(farmer.id)) {
            throw ApiException.notFound("Purchase order not found"); // do not reveal other farmers' orders
        }
        if (!"PENDING".equals(po.status)) {
            throw ApiException.conflict("This purchase order has already been answered");
        }
        po.status = accept ? "CONFIRMED" : "DECLINED";
        purchaseOrders.save(po);
        notifications.notifyAdmins("Purchase order #" + po.id + (accept ? " confirmed" : " declined"),
                "The farmer has " + (accept ? "confirmed" : "declined") + " purchase order #" + po.id + ".", "SUPPLY");
        return po;
    }

    @Transactional
    public PurchaseOrder cancelPurchaseOrder(Long poId) {
        PurchaseOrder po = purchaseOrders.findById(poId).orElseThrow(() -> ApiException.notFound("Purchase order not found"));
        if (!"PENDING".equals(po.status) && !"CONFIRMED".equals(po.status)) {
            throw ApiException.conflict("Only pending or confirmed purchase orders can be cancelled");
        }
        po.status = "CANCELLED";
        purchaseOrders.save(po);
        notifications.notify(po.farmer.user, "Purchase order #" + po.id + " cancelled",
                "The platform cancelled purchase order #" + po.id + ".", "SUPPLY");
        return po;
    }

    @Transactional
    public ReceivingBatch receive(Long poId, ReceiveRequest req) {
        PurchaseOrder po = purchaseOrders.findById(poId).orElseThrow(() -> ApiException.notFound("Purchase order not found"));
        if (!"CONFIRMED".equals(po.status) && !"RECEIVED".equals(po.status)) {
            throw ApiException.conflict("The farmer must confirm the purchase order before products can be received");
        }
                boolean paid = farmerPayments.findByFarmerIdOrderByIdDesc(po.farmer.id).stream()
                .anyMatch(fp -> fp.purchaseOrder.id.equals(po.id) && "PAID".equals(fp.status));
        if (!paid) {
            throw ApiException.conflict("Pay the farmer for this purchase order before recording the delivery");
        }
        List<PurchaseOrderItem> items = poItems.findByPurchaseOrderId(po.id);
        int existing = batches.findByPurchaseOrderId(po.id).size();

        ReceivingBatch batch = new ReceivingBatch();
        batch.purchaseOrder = po;
        batch.batchNumber = "RB-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + po.id + "-" + (existing + 1);
        batch.receivedDate = req.receivedDate() != null ? req.receivedDate() : LocalDate.now();
        batch.quantity = req.quantity();
        batch.unit = (req.unit() != null && !req.unit().isBlank()) ? req.unit()
                : (items.isEmpty() ? null : items.get(0).product.unit);
        batch.qualityStatus = "PENDING";
        batch = batches.save(batch);

        po.status = "RECEIVED";
        purchaseOrders.save(po);
        notifications.notify(po.farmer.user, "Products received",
                "Your delivery for purchase order #" + po.id + " was received and is awaiting quality inspection.", "SUPPLY");
        return batch;
    }

    @Transactional
    public QualityInspection inspect(Long batchId, InspectionRequest req, AuthUser inspector) {
        String result = req.result().trim().toUpperCase();
        if (!result.equals("PASSED") && !result.equals("FAILED")) {
            throw ApiException.badRequest("result must be PASSED or FAILED");
        }
        ReceivingBatch batch = batches.findById(batchId).orElseThrow(() -> ApiException.notFound("Batch not found"));
        if (!"PENDING".equals(batch.qualityStatus)) {
            throw ApiException.conflict("This batch has already been inspected");
        }
        User inspectorUser = users.findById(inspector.userId()).orElseThrow(() -> ApiException.notFound("User not found"));

        QualityInspection qi = new QualityInspection();
        qi.batch = batch;
        qi.inspectedBy = inspectorUser;
        qi.result = result;
        qi.notes = req.notes();
        qi = inspections.save(qi);

        batch.qualityStatus = result.equals("PASSED") ? "ACCEPTED" : "REJECTED";
        batches.save(batch);

        // Roll the result up to the purchase order.
        PurchaseOrder po = batch.purchaseOrder;
        List<ReceivingBatch> all = batches.findByPurchaseOrderId(po.id);
        boolean anyAccepted = all.stream().anyMatch(b -> "ACCEPTED".equals(b.qualityStatus));
        boolean anyPending = all.stream().anyMatch(b -> "PENDING".equals(b.qualityStatus));
        if (anyAccepted) {
            po.status = "ACCEPTED";
        } else if (!anyPending) {
            po.status = "REJECTED";
        }
        purchaseOrders.save(po);

        notifications.notify(po.farmer.user,
                result.equals("PASSED") ? "Delivery accepted" : "Delivery rejected",
                "Your delivery for purchase order #" + po.id + (result.equals("PASSED")
                        ? " passed quality inspection." : " did not pass quality inspection."), "SUPPLY");
        return qi;
    }

    @Transactional
    public FarmerPayment createFarmerPayment(CreateFarmerPaymentRequest req) {
        PurchaseOrder po = purchaseOrders.findById(req.purchaseOrderId())
                .orElseThrow(() -> ApiException.notFound("Purchase order not found"));
       if (!"CONFIRMED".equals(po.status) && !"RECEIVED".equals(po.status) && !"ACCEPTED".equals(po.status)) {
            throw ApiException.conflict("The farmer must confirm the purchase order before it can be paid");
        }
        if (farmerPayments.existsForPurchaseOrder(po.id)) {
            throw ApiException.conflict("A payment already exists for this purchase order");
        }
        FarmerPayment fp = new FarmerPayment();
        fp.farmer = po.farmer;
        fp.purchaseOrder = po;
        fp.amount = (req.amount() != null ? req.amount() : po.totalAmount).setScale(2, RoundingMode.HALF_UP);
        fp.status = "PENDING";
        return farmerPayments.save(fp);
    }

    @Transactional
    public FarmerPayment markFarmerPaymentPaid(Long id, FarmerPaymentUpdateRequest req) {
        FarmerPayment fp = farmerPayments.findById(id).orElseThrow(() -> ApiException.notFound("Farmer payment not found"));
        if (!"PENDING".equals(fp.status)) {
            throw ApiException.conflict("This payment has already been completed");
        }
        fp.status = "PAID";
        fp.paymentMethod = req.paymentMethod();
        fp.reference = req.reference();
        fp.paymentDate = req.paymentDate() != null ? req.paymentDate() : LocalDate.now();
        farmerPayments.save(fp);
        notifications.notify(fp.farmer.user, "Payment sent",
                "A payment of R" + fp.amount.toPlainString() + " for purchase order #" + fp.purchaseOrder.id + " has been made.", "PAYMENT");
        return fp;
    }
        @Transactional
    public void markFarmerPaymentPaidOnline(Long id, String reference) {
        FarmerPayment fp = farmerPayments.findById(id).orElseThrow(() -> ApiException.notFound("Farmer payment not found"));
        if (!"PENDING".equals(fp.status)) {
            return;
        }
        fp.status = "PAID";
        fp.paymentMethod = "PAYFAST";
        fp.reference = reference;
        fp.paymentDate = LocalDate.now();
        farmerPayments.save(fp);
        notifications.notify(fp.farmer.user, "Payment sent",
                "A payment of R" + fp.amount.toPlainString() + " for purchase order #" + fp.purchaseOrder.id + " has been made.", "PAYMENT");
    }
}
