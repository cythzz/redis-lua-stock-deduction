package com.cythzz.inventory.order;

public record OrderSubmission(
    Status status,
    String orderId,
    String requestId,
    String sku,
    int quantity,
    long remainingStock,
    String message) {
  public enum Status {
    ACCEPTED,
    DUPLICATE,
    SOLD_OUT,
    SKU_NOT_FOUND,
    PUBLISH_FAILED
  }
}
