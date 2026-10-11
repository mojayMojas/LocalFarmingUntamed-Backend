package com.farmplatform.web;

import com.farmplatform.common.ApiException;
import com.farmplatform.dto.Dtos.*;
import com.farmplatform.dto.Views.PoView;
import com.farmplatform.entity.*;
import com.farmplatform.repository.Repos.*;
import com.farmplatform.security.AuthUser;
import com.farmplatform.service.AuthService;
import com.farmplatform.service.CurrentActors;
import com.farmplatform.service.SupplyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything a farmer can see or do. Farmers only ever touch their own records and never see customer data. */
@RestController
@RequestMapping("/api/farmer")
public class FarmerController {
    private static final Set<String> AVAILABILITY = Set.of("AVAILABLE", "UNAVAILABLE");

    private final CurrentActors actors;
    private final AuthService auth;
    private final UserRepo users;
    private final FarmerLocationRepo locations;
    private final FarmerProductRepo farmerProducts;
    private final ProductRepo products;
    private final PurchaseOrderRepo purchaseOrders;
    private final PurchaseOrderItemRepo poItems;
    private final FarmerPaymentRepo farmerPayments;
    private final SupplyService supply;

    public FarmerController(CurrentActors actors, AuthService auth, UserRepo users, FarmerLocationRepo locations,
                            FarmerProductRepo farmerProducts, ProductRepo products, PurchaseOrderRepo purchaseOrders,
                            PurchaseOrderItemRepo poItems, FarmerPaymentRepo farmerPayments, SupplyService supply) {
        this.actors = actors;
        this.auth = auth;
        this.users = users;
        this.locations = locations;
        this.farmerProducts = farmerProducts;
        this.products = products;
        this.purchaseOrders = purchaseOrders;
        this.poItems = poItems;
        this.farmerPayments = farmerPayments;
        this.supply = supply;
    }

    // ---------- profile & locations ----------

    @GetMapping("/profile")
    public Map<String, Object> profile(@AuthenticationPrincipal AuthUser me) {
        Farmer f = actors.farmer(me);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", auth.me(me));
        m.put("verificationStatus", f.verificationStatus);
        m.put("registrationDate", f.registrationDate);
        m.put("locations", locations.findByFarmerId(f.id));
        return m;
    }

    @PutMapping("/profile")
    public AuthResponse updateProfile(@AuthenticationPrincipal AuthUser me, @RequestBody ProfileUpdateRequest req) {
        User u = actors.user(me);
        if (req.firstName() != null && !req.firstName().isBlank()) u.firstName = req.firstName().trim();
        if (req.lastName() != null && !req.lastName().isBlank()) u.lastName = req.lastName().trim();
        if (req.phone() != null) u.phone = req.phone();
        users.save(u);
        return auth.me(me);
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public FarmerLocation addLocation(@AuthenticationPrincipal AuthUser me, @RequestBody LocationRequest req) {
        FarmerLocation loc = new FarmerLocation();
        loc.farmer = actors.farmer(me);
        loc.province = req.province();
        loc.municipality = req.municipality();
        loc.town = req.town();
        loc.address = req.address();
        return locations.save(loc);
    }

    @DeleteMapping("/locations/{id}")
    public ResponseEntity<Void> deleteLocation(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Farmer f = actors.farmer(me);
        FarmerLocation loc = locations.findById(id).orElseThrow(() -> ApiException.notFound("Location not found"));
        if (!loc.farmer.id.equals(f.id)) {
            throw ApiException.notFound("Location not found");
        }
        locations.delete(loc);
        return ResponseEntity.noContent().build();
    }

    // ---------- products the farmer can supply ----------

    @GetMapping("/products")
    public List<FarmerProduct> myProducts(@AuthenticationPrincipal AuthUser me) {
        return farmerProducts.findByFarmerId(actors.farmer(me).id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public FarmerProduct addProduct(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody FarmerProductRequest req) {
        Farmer f = actors.farmer(me);
        if ("REJECTED".equals(f.verificationStatus) || "SUSPENDED".equals(f.verificationStatus)) {
            throw ApiException.forbidden("Your farmer account is " + f.verificationStatus.toLowerCase() + ". Please contact the platform owner.");
        }
        Product product = products.findById(req.productId()).orElseThrow(() -> ApiException.notFound("Product not found"));
        boolean exists = farmerProducts.findByFarmerId(f.id).stream().anyMatch(fp -> fp.product.id.equals(product.id));
        if (exists) {
            throw ApiException.conflict("You already listed this product. Edit the existing entry instead.");
        }
        FarmerProduct fp = new FarmerProduct();
        fp.farmer = f;
        fp.product = product;
        apply(fp, req, product);
        return farmerProducts.save(fp);
    }

    @PutMapping("/products/{id}")
    public FarmerProduct updateProduct(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                       @Valid @RequestBody FarmerProductRequest req) {
        FarmerProduct fp = ownedProduct(actors.farmer(me), id);
        apply(fp, req, fp.product);
        return farmerProducts.save(fp);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        farmerProducts.delete(ownedProduct(actors.farmer(me), id));
        return ResponseEntity.noContent().build();
    }

    // ---------- purchase orders (supply requests) ----------

    @GetMapping("/purchase-orders")
    @Transactional(readOnly = true)
    public List<PoView> myPurchaseOrders(@AuthenticationPrincipal AuthUser me) {
        Farmer f = actors.farmer(me);
        return purchaseOrders.findByFarmerIdOrderByIdDesc(f.id).stream()
                .map(po -> new PoView(po, poItems.findByPurchaseOrderId(po.id)))
                .toList();
    }

    @PostMapping("/purchase-orders/{id}/confirm")
    public PurchaseOrder confirm(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return supply.farmerRespond(actors.farmer(me), id, true);
    }

    @PostMapping("/purchase-orders/{id}/decline")
    public PurchaseOrder decline(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return supply.farmerRespond(actors.farmer(me), id, false);
    }

    // ---------- payments received ----------

    @GetMapping("/payments")
    public List<FarmerPayment> myPayments(@AuthenticationPrincipal AuthUser me) {
        return farmerPayments.findByFarmerIdOrderByIdDesc(actors.farmer(me).id);
    }

    // ---------- helpers ----------

    private FarmerProduct ownedProduct(Farmer f, Long id) {
        FarmerProduct fp = farmerProducts.findById(id).orElseThrow(() -> ApiException.notFound("Product entry not found"));
        if (!fp.farmer.id.equals(f.id)) {
            throw ApiException.notFound("Product entry not found");
        }
        return fp;
    }

    private void apply(FarmerProduct fp, FarmerProductRequest req, Product product) {
        fp.availableQuantity = req.availableQuantity();
        fp.unit = (req.unit() != null && !req.unit().isBlank()) ? req.unit() : product.unit;
        fp.price = req.price();
        String status = req.availabilityStatus() == null ? "AVAILABLE" : req.availabilityStatus().trim().toUpperCase();
        if (!AVAILABILITY.contains(status)) {
            throw ApiException.badRequest("availabilityStatus must be AVAILABLE or UNAVAILABLE");
        }
        fp.availabilityStatus = status;
    }
}
