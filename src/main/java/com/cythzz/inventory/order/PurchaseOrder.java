package com.cythzz.inventory.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(
    name = "purchase_orders",
    uniqueConstraints = @UniqueConstraint(name = "uk_purchase_order_request", columnNames = "request_id"))
public class PurchaseOrder {
  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "request_id", nullable = false, length = 64)
  private String requestId;

  @Column(nullable = false, length = 64)
  private String sku;

  @Column(nullable = false)
  private int quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private OrderStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  private long version;

  protected PurchaseOrder() {
  }

  public static PurchaseOrder pending(OrderCreatedEvent event) {
    var order = new PurchaseOrder();
    order.id = event.orderId();
    order.requestId = event.requestId();
    order.sku = event.sku();
    order.quantity = event.quantity();
    order.status = OrderStatus.PENDING_PAYMENT;
    order.createdAt = event.createdAt();
    order.updatedAt = event.createdAt();
    return order;
  }

  public void markPaid(Instant now) {
    if (status != OrderStatus.PENDING_PAYMENT) {
      throw new IllegalStateException("Only pending orders can be paid");
    }
    status = OrderStatus.PAID;
    updatedAt = now;
  }

  public boolean cancel(Instant now) {
    if (status != OrderStatus.PENDING_PAYMENT) {
      return false;
    }
    status = OrderStatus.CANCELLED;
    updatedAt = now;
    return true;
  }

  public String getId() {
    return id;
  }

  public String getRequestId() {
    return requestId;
  }

  public String getSku() {
    return sku;
  }

  public int getQuantity() {
    return quantity;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
