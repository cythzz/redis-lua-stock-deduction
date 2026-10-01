package com.cythzz.inventory.order;

import java.time.Instant;

public record OrderCreatedEvent(
    String orderId,
    String requestId,
    String sku,
    int quantity,
    Instant createdAt) {
}
