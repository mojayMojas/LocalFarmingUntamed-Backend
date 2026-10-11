package com.farmplatform.web;

import com.farmplatform.dto.Dtos.*;
import com.farmplatform.dto.Views.PoView;
import com.farmplatform.entity.FarmerPayment;
import com.farmplatform.entity.PurchaseOrder;
import com.farmplatform.entity.QualityInspection;
import com.farmplatform.entity.ReceivingBatch;
import com.farmplatform.repository.Repos.*;
import com.farmplatform.security.AuthUser;
import com.farmplatform.service.SupplyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Administrator: buying from farmers, receiving, quality inspection and farmer payments. */
@RestController
@RequestMapping("/api/admin")
public class AdminSupplyController {
    private final SupplyService supply;
    private final PurchaseOrderRepo purchaseOrders;
    private final PurchaseOrderItemRepo poItems;
    private final ReceivingBatchRepo batches;
    private final QualityInspectionRepo inspections;
    private final FarmerPaymentRepo farmerPayments;

    public AdminSupplyController(SupplyService supply, PurchaseOrderRepo purchaseOrders, PurchaseOrderItemRepo poItems,
                                 ReceivingBatchRepo batches, QualityInspectionRepo inspections,
                                 FarmerPaymentRepo farmerPayments) {
        this.supply = supply;
        this.purchaseOrders = purchaseOrders;
        this.poItems = poItems;
        this.batches = batches;
        this.inspections = inspections;
        this.farmerPayments = farmerPayments;
    }

    // ---------- purchase orders ----------

    @GetMapping("/purchase-orders")
    @Transactional(readOnly = true)
    public List<PoView> purchaseOrders() {
        return purchaseOrders.findAllByOrderByIdDesc().stream()
                .map(po -> new PoView(po, poItems.findByPurchaseOrderId(po.id)))
                .toList();
    }

    @PostMapping("/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PoView createPurchaseOrder(@Valid @RequestBody CreatePurchaseOrderRequest req) {
        PurchaseOrder po = supply.createPurchaseOrder(req);
        return new PoView(po, poItems.findByPurchaseOrderId(po.id));
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    public PurchaseOrder cancel(@PathVariable Long id) {
        return supply.cancelPurchaseOrder(id);
    }

    /** Record the farmer's delivery arriving at the platform. */
    @PostMapping("/purchase-orders/{id}/receive")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceivingBatch receive(@PathVariable Long id, @Valid @RequestBody ReceiveRequest req) {
        return supply.receive(id, req);
    }

    // ---------- receiving & quality ----------

    @GetMapping("/receiving-batches")
    public List<ReceivingBatch> receivingBatches() {
        return batches.findAllByOrderByIdDesc();
    }

    @PostMapping("/receiving-batches/{id}/inspect")
    @ResponseStatus(HttpStatus.CREATED)
    public QualityInspection inspect(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                     @Valid @RequestBody InspectionRequest req) {
        return supply.inspect(id, req, me);
    }

    @GetMapping("/inspections")
    public List<QualityInspection> inspections() {
        return inspections.findAllByOrderByIdDesc();
    }

    // ---------- farmer payments ----------

    @GetMapping("/farmer-payments")
    public List<FarmerPayment> farmerPayments() {
        return farmerPayments.findAllByOrderByIdDesc();
    }

    @PostMapping("/farmer-payments")
    @ResponseStatus(HttpStatus.CREATED)
    public FarmerPayment createFarmerPayment(@Valid @RequestBody CreateFarmerPaymentRequest req) {
        return supply.createFarmerPayment(req);
    }

    @PostMapping("/farmer-payments/{id}/paid")
    public FarmerPayment markPaid(@PathVariable Long id, @Valid @RequestBody FarmerPaymentUpdateRequest req) {
        return supply.markFarmerPaymentPaid(id, req);
    }
}
