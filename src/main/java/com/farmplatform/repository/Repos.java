package com.farmplatform.repository;

import com.farmplatform.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** All Spring Data repositories, grouped in one file (enabled via considerNestedRepositories). */
public final class Repos {
    private Repos() {}

    public interface RoleRepo extends JpaRepository<Role, Long> {
        Optional<Role> findByRoleName(String roleName);
    }

    public interface UserRepo extends JpaRepository<User, Long> {
        Optional<User> findByEmailIgnoreCase(String email);
        boolean existsByEmailIgnoreCase(String email);
        List<User> findByRoleRoleName(String roleName);
    }

    public interface FarmerRepo extends JpaRepository<Farmer, Long> {
        Optional<Farmer> findByUserId(Long userId);
        List<Farmer> findByVerificationStatus(String status);
    }

    public interface FarmerLocationRepo extends JpaRepository<FarmerLocation, Long> {
        List<FarmerLocation> findByFarmerId(Long farmerId);
    }

    public interface CategoryRepo extends JpaRepository<ProductCategory, Long> {}

    public interface ProductRepo extends JpaRepository<Product, Long> {}

    public interface FarmerProductRepo extends JpaRepository<FarmerProduct, Long> {
        List<FarmerProduct> findByFarmerId(Long farmerId);
    }

    public interface PurchaseOrderRepo extends JpaRepository<PurchaseOrder, Long> {
        List<PurchaseOrder> findByFarmerIdOrderByIdDesc(Long farmerId);
        List<PurchaseOrder> findAllByOrderByIdDesc();
    }

    public interface PurchaseOrderItemRepo extends JpaRepository<PurchaseOrderItem, Long> {
        List<PurchaseOrderItem> findByPurchaseOrderId(Long purchaseOrderId);
    }

    public interface ReceivingBatchRepo extends JpaRepository<ReceivingBatch, Long> {
        List<ReceivingBatch> findByPurchaseOrderId(Long purchaseOrderId);
        List<ReceivingBatch> findAllByOrderByIdDesc();
    }

    public interface QualityInspectionRepo extends JpaRepository<QualityInspection, Long> {
        List<QualityInspection> findByBatchId(Long batchId);
        List<QualityInspection> findAllByOrderByIdDesc();
    }

    public interface ProcessingBatchRepo extends JpaRepository<ProcessingBatch, Long> {
        List<ProcessingBatch> findAllByOrderByIdDesc();
    }

    public interface InventoryRepo extends JpaRepository<Inventory, Long> {
        List<Inventory> findAllByOrderByIdDesc();

        boolean existsByProcessingBatchId(Long processingId);

        List<Inventory> findByStatusAndQuantityGreaterThan(String status, BigDecimal quantity);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select i from Inventory i where i.id = :id")
        Optional<Inventory> lockById(@Param("id") Long id);
    }

    public interface CustomerRepo extends JpaRepository<Customer, Long> {
        Optional<Customer> findByUserId(Long userId);
    }

    public interface CustomerRequirementRepo extends JpaRepository<CustomerRequirement, Long> {
        List<CustomerRequirement> findByCustomerIdOrderByIdDesc(Long customerId);
        List<CustomerRequirement> findAllByOrderByIdDesc();
    }

    public interface CustomerOrderRepo extends JpaRepository<CustomerOrder, Long> {
        List<CustomerOrder> findByCustomerIdOrderByIdDesc(Long customerId);
        List<CustomerOrder> findAllByOrderByIdDesc();
    }

    public interface CustomerOrderItemRepo extends JpaRepository<CustomerOrderItem, Long> {
        @Query("select i from CustomerOrderItem i where i.order.id = :orderId")
        List<CustomerOrderItem> findByOrderId(@Param("orderId") Long orderId);
    }

    public interface PaymentRepo extends JpaRepository<Payment, Long> {
        Optional<Payment> findByOzowReference(String ozowReference);

        @Query("select p from Payment p where p.order.id = :orderId order by p.id desc")
        List<Payment> findByOrderId(@Param("orderId") Long orderId);

        List<Payment> findAllByOrderByIdDesc();

        @Query("select coalesce(sum(p.amount), 0) from Payment p where p.paymentStatus = 'COMPLETE'")
        BigDecimal sumCompleted();
    }

    public interface FarmerPaymentRepo extends JpaRepository<FarmerPayment, Long> {
        List<FarmerPayment> findByFarmerIdOrderByIdDesc(Long farmerId);
        List<FarmerPayment> findAllByOrderByIdDesc();

        @Query("select count(fp) > 0 from FarmerPayment fp where fp.purchaseOrder.id = :poId")
        boolean existsForPurchaseOrder(@Param("poId") Long poId);

        @Query("select coalesce(sum(fp.amount), 0) from FarmerPayment fp where fp.status = 'PAID'")
        BigDecimal sumPaid();
    }

    public interface DriverRepo extends JpaRepository<Driver, Long> {
        Optional<Driver> findByUserId(Long userId);
    }

    public interface DeliveryRepo extends JpaRepository<Delivery, Long> {
        @Query("select d from Delivery d where d.driver.id = :driverId order by d.id desc")
        List<Delivery> findByDriverId(@Param("driverId") Long driverId);

        @Query("select d from Delivery d where d.order.id = :orderId order by d.id desc")
        List<Delivery> findByOrderId(@Param("orderId") Long orderId);

        List<Delivery> findAllByOrderByIdDesc();
    }

    public interface InvoiceRepo extends JpaRepository<Invoice, Long> {
        @Query("select i from Invoice i where i.order.id = :orderId")
        Optional<Invoice> findByOrderId(@Param("orderId") Long orderId);

        @Query("select i from Invoice i where i.order.customer.id = :customerId order by i.id desc")
        List<Invoice> findByCustomerId(@Param("customerId") Long customerId);

        List<Invoice> findAllByOrderByIdDesc();
    }

    public interface NotificationRepo extends JpaRepository<Notification, Long> {
        @Query("select n from Notification n where n.user.id = :userId order by n.id desc")
        List<Notification> findByUserId(@Param("userId") Long userId);
    }
}
