package com.cythzz.inventory.order;

import java.time.Instant;

public record OrderView(
    String orderId,
    String requestId,
    String sku,
    int quantity,
    OrderStatus status,
    Instant createdAt,
    Instant updatedAt) {
  static OrderView from(PurchaseOrder order) {
    return new OrderView(
        order.getId(),
        order.getRequestId(),
        order.getSku(),
        order.getQuantity(),
        order.getStatus(),
        order.getCreatedAt(),
        order.getUpdatedAt());
  }
}
