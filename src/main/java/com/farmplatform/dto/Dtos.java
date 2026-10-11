package com.farmplatform.dto;

import com.farmplatform.entity.CustomerRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request and response shapes. Customer/farmer-facing views deliberately omit anything that identifies the other side. */
public final class Dtos {
    private Dtos() {}

    // ---------- auth ----------
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String token, Long userId, String firstName, String lastName, String email,
                               String phone, String role, String profileStatus) {}

    public record RegisterFarmerRequest(@NotBlank String firstName, @NotBlank String lastName,
                                        @NotBlank @Email String email, String phone,
                                        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password,
                                        String province, String municipality, String town, String address) {}

    public record RegisterCustomerRequest(@NotBlank String firstName, @NotBlank String lastName,
                                          @NotBlank @Email String email, String phone,
                                          @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password,
                                          @NotBlank String companyName, String customerType,
                                          String businessRegistration, @NotBlank String address) {}

    public record CreateDriverRequest(@NotBlank String firstName, @NotBlank String lastName,
                                      @NotBlank @Email String email, String phone,
                                      @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password,
                                      String vehicleRegistration, String vehicleType) {}

    public record UpdateDriverRequest(String firstName, String lastName, String phone,
                                      String vehicleRegistration, String vehicleType, String status) {}

    public record ProfileUpdateRequest(String firstName, String lastName, String phone) {}

    public record CustomerProfileUpdateRequest(String firstName, String lastName, String phone, String companyName,
                                               String customerType, String businessRegistration, String address) {}

    // ---------- farmer ----------
    public record LocationRequest(String province, String municipality, String town, String address) {}

    public record FarmerProductRequest(@NotNull Long productId,
                                       @NotNull @DecimalMin("0") BigDecimal availableQuantity,
                                       String unit,
                                       @NotNull @DecimalMin("0") BigDecimal price,
                                       String availabilityStatus) {}

    // ---------- catalogue ----------
    public record CategoryRequest(@NotBlank String categoryName, String description) {}

    public record ProductRequest(@NotNull Long categoryId, @NotBlank String productName,
                                 String productType, String description, String unit) {}

    // ---------- purchasing ----------
    public record PoItemRequest(@NotNull Long productId,
                                @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
                                @NotNull @DecimalMin("0") BigDecimal unitPrice) {}

    public record CreatePurchaseOrderRequest(@NotNull Long farmerId, @NotEmpty @Valid List<PoItemRequest> items) {}

    public record ReceiveRequest(@NotNull @DecimalMin(value = "0.001") BigDecimal quantity, String unit,
                                 LocalDate receivedDate) {}

    /** result must be PASSED or FAILED */
    public record InspectionRequest(@NotBlank String result, String notes) {}

    /** amount is optional: it defaults to the purchase order total (set it lower if only part of the delivery was accepted). */
    public record CreateFarmerPaymentRequest(@NotNull Long purchaseOrderId, @DecimalMin("0.01") BigDecimal amount) {}

    public record FarmerPaymentUpdateRequest(@NotBlank String paymentMethod, String reference, LocalDate paymentDate) {}

    // ---------- processing & inventory ----------
    public record ProcessingRequest(@NotNull Long inputBatchId, @NotNull Long productId, @NotBlank String processingType,
                                    @NotNull @DecimalMin(value = "0.001") BigDecimal quantityIn,
                                    @NotNull @DecimalMin(value = "0.001") BigDecimal quantityOut) {}

    public record CompleteProcessingRequest(BigDecimal quantityOut) {}

    public record InventoryRequest(@NotNull Long processingId,
                                   @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
                                   String unit, String storageLocation,
                                   @NotNull @DecimalMin(value = "0.01") BigDecimal sellingPrice) {}

    public record InventoryUpdateRequest(BigDecimal quantity, BigDecimal sellingPrice, String storageLocation, String status) {}

    public record StatusRequest(@NotBlank String status) {}

    // ---------- customers ----------
    public record RequirementRequest(@NotNull Long productId, @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
                                     String unit, LocalDate requiredDate, String specifications) {}

    public record OrderItemRequest(@NotNull Long inventoryId, @NotNull @DecimalMin(value = "0.001") BigDecimal quantity) {}

    public record PlaceOrderRequest(@NotEmpty @Valid List<OrderItemRequest> items) {}

    public record DeliveryRequest(@NotNull Long orderId, @NotNull Long driverId, @NotNull LocalDate scheduledDate,
                                  String deliveryAddress) {}

    // ---------- customer-safe views (no farmer information anywhere) ----------
    public record CatalogItem(Long inventoryId, Long productId, String productName, String category, String unit,
                              BigDecimal availableQuantity, BigDecimal sellingPrice) {}

    public record OrderItemView(Long productId, String productName, String unit, BigDecimal quantity,
                                BigDecimal unitPrice, BigDecimal subtotal) {}

    public record DeliveryCustomerView(Long id, String status, LocalDate scheduledDate, LocalDate deliveryDate,
                                       String deliveryAddress, String driverName, String driverPhone,
                                       String vehicleType, String vehicleRegistration) {}

    public record OrderView(Long id, LocalDate orderDate, String status, BigDecimal totalAmount,
                            List<OrderItemView> items, Long invoiceId, String invoiceNumber, String invoiceStatus,
                            String paymentStatus, DeliveryCustomerView delivery) {}

    public record AvailabilityResponse(Long productId, String productName, String unit, BigDecimal requested,
                                       BigDecimal available, boolean sufficient, BigDecimal shortfall) {}

    // ---------- driver view ----------
    public record DeliveryDriverView(Long id, Long orderId, String status, LocalDate scheduledDate,
                                     LocalDate deliveryDate, String deliveryAddress, String customerCompany,
                                     String contactName, String contactPhone, List<OrderItemView> items) {}

    // ---------- payments ----------
    public record PaymentInitResponse(Long paymentId, String reference, String redirectUrl, boolean mock) {}

    public record PaymentStatusView(String reference, String paymentStatus, Long orderId, String orderStatus) {}

    // ---------- admin ----------
    public record AdminRequirementView(CustomerRequirement requirement, BigDecimal availableStock, BigDecimal shortfall) {}
}
