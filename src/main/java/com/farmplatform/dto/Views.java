package com.farmplatform.dto;

import com.farmplatform.dto.Dtos.OrderItemView;
import com.farmplatform.entity.CustomerOrder;
import com.farmplatform.entity.PurchaseOrder;
import com.farmplatform.entity.PurchaseOrderItem;

import java.util.List;

/** Composite responses used by the farmer and admin screens. */
public final class Views {
    private Views() {}

    public record PoView(PurchaseOrder purchaseOrder, List<PurchaseOrderItem> items) {}

    /** Admin-only: includes the customer (via the order entity) and the payment status. */
    public record AdminOrderView(CustomerOrder order, List<OrderItemView> items, String paymentStatus) {}
}
