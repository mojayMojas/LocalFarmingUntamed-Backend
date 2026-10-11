package com.farmplatform.service;

import com.farmplatform.common.ApiException;
import com.farmplatform.dto.Dtos.*;
import com.farmplatform.entity.Inventory;
import com.farmplatform.entity.ProcessingBatch;
import com.farmplatform.entity.Product;
import com.farmplatform.entity.ReceivingBatch;
import com.farmplatform.repository.Repos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/** Turning accepted raw batches into finished stock that customers can buy. */
@Service
public class ProcessingService {
    private static final Set<String> INVENTORY_STATUSES = Set.of("AVAILABLE", "OUT_OF_STOCK", "HIDDEN");

    private final ReceivingBatchRepo batches;
    private final ProductRepo products;
    private final ProcessingBatchRepo processing;
    private final InventoryRepo inventory;

    public ProcessingService(ReceivingBatchRepo batches, ProductRepo products, ProcessingBatchRepo processing,
                             InventoryRepo inventory) {
        this.batches = batches;
        this.products = products;
        this.processing = processing;
        this.inventory = inventory;
    }

    @Transactional
    public ProcessingBatch create(ProcessingRequest req) {
        ReceivingBatch input = batches.findById(req.inputBatchId()).orElseThrow(() -> ApiException.notFound("Receiving batch not found"));
        if (!"ACCEPTED".equals(input.qualityStatus)) {
            throw ApiException.conflict("Only batches that passed quality inspection can be processed");
        }
        Product product = products.findById(req.productId()).orElseThrow(() -> ApiException.notFound("Product not found"));
        if (req.quantityOut().compareTo(req.quantityIn()) > 0) {
            throw ApiException.badRequest("Output quantity cannot be more than the input quantity");
        }
        ProcessingBatch pb = new ProcessingBatch();
        pb.inputBatch = input;
        pb.product = product;
        pb.processingType = req.processingType();
        pb.quantityIn = req.quantityIn();
        pb.quantityOut = req.quantityOut();
        pb.status = "IN_PROGRESS";
        return processing.save(pb);
    }

    @Transactional
    public ProcessingBatch complete(Long id, CompleteProcessingRequest req) {
        ProcessingBatch pb = processing.findById(id).orElseThrow(() -> ApiException.notFound("Processing batch not found"));
        if (!"IN_PROGRESS".equals(pb.status)) {
            throw ApiException.conflict("This processing batch is already completed");
        }
        if (req != null && req.quantityOut() != null) {
            if (req.quantityOut().signum() <= 0 || req.quantityOut().compareTo(pb.quantityIn) > 0) {
                throw ApiException.badRequest("Output quantity must be above 0 and not more than the input quantity");
            }
            pb.quantityOut = req.quantityOut();
        }
        pb.status = "COMPLETED";
        pb.processedDate = LocalDate.now();
        return processing.save(pb);
    }

    @Transactional
    public Inventory addToInventory(InventoryRequest req) {
        ProcessingBatch pb = processing.findById(req.processingId()).orElseThrow(() -> ApiException.notFound("Processing batch not found"));
        if (!"COMPLETED".equals(pb.status)) {
            throw ApiException.conflict("Complete the processing batch before adding its output to inventory");
        }
        if (inventory.existsByProcessingBatchId(pb.id)) {
            throw ApiException.conflict("This processing run has already been added to inventory. Edit that stock line instead.");
        }
        if (req.quantity().compareTo(pb.quantityOut) > 0) {
            throw ApiException.badRequest("Cannot stock more than the processed output (" + pb.quantityOut.toPlainString() + ")");
        }
        Inventory inv = new Inventory();
        inv.processingBatch = pb;
        inv.product = pb.product;
        inv.quantity = req.quantity();
        inv.unit = (req.unit() != null && !req.unit().isBlank()) ? req.unit() : pb.product.unit;
        inv.storageLocation = req.storageLocation();
        inv.sellingPrice = req.sellingPrice().setScale(2, RoundingMode.HALF_UP);
        inv.status = "AVAILABLE";
        return inventory.save(inv);
    }

    @Transactional
    public Inventory updateInventory(Long id, InventoryUpdateRequest req) {
        Inventory inv = inventory.lockById(id).orElseThrow(() -> ApiException.notFound("Inventory line not found"));
        if (req.quantity() != null) {
            if (req.quantity().signum() < 0) {
                throw ApiException.badRequest("Quantity cannot be negative");
            }
            inv.quantity = req.quantity();
        }
        if (req.sellingPrice() != null) {
            if (req.sellingPrice().compareTo(new BigDecimal("0.01")) < 0) {
                throw ApiException.badRequest("Selling price must be above 0");
            }
            inv.sellingPrice = req.sellingPrice().setScale(2, RoundingMode.HALF_UP);
        }
        if (req.storageLocation() != null) {
            inv.storageLocation = req.storageLocation();
        }
        if (req.status() != null && !req.status().isBlank()) {
            String s = req.status().trim().toUpperCase();
            if (!INVENTORY_STATUSES.contains(s)) {
                throw ApiException.badRequest("status must be AVAILABLE, OUT_OF_STOCK or HIDDEN");
            }
            inv.status = s;
        }
        if (inv.quantity != null && inv.quantity.signum() == 0) {
            inv.status = "OUT_OF_STOCK";
        } else if ("OUT_OF_STOCK".equals(inv.status) && inv.quantity != null && inv.quantity.signum() > 0
                && (req.status() == null || req.status().isBlank())) {
            inv.status = "AVAILABLE"; // stock was topped up
        }
        inv.updatedAt = LocalDateTime.now();
        return inventory.save(inv);
    }
}
