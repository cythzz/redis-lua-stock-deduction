package com.cythzz.inventory.order;

import com.cythzz.inventory.repository.StockReservationGateway;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final PurchaseOrderRepository orderRepository;
  private final StockReservationGateway reservationGateway;
  private final MeterRegistry meterRegistry;
  private final Duration paymentTimeout;

  public OrderService(
      PurchaseOrderRepository orderRepository,
      StockReservationGateway reservationGateway,
      MeterRegistry meterRegistry,
      @Value("${app.order.payment-timeout:15m}") Duration paymentTimeout) {
    this.orderRepository = orderRepository;
    this.reservationGateway = reservationGateway;
    this.meterRegistry = meterRegistry;
    this.paymentTimeout = paymentTimeout;
  }

  @Transactional(readOnly = true)
  public OrderView find(String orderId) {
    return OrderView.from(orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId)));
  }

  @Transactional
  public OrderView markPaid(String orderId) {
    PurchaseOrder order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
    order.markPaid(Instant.now());
    meterRegistry.counter("orders.state.changed", "state", "paid").increment();
    return OrderView.from(order);
  }

  @Scheduled(fixedDelayString = "${app.order.cancellation-scan-delay:60000}")
  @Transactional
  public void cancelExpiredOrders() {
    Instant deadline = Instant.now().minus(paymentTimeout);
    for (PurchaseOrder order :
        orderRepository.findTop100ByStatusAndCreatedAtBefore(
            OrderStatus.PENDING_PAYMENT, deadline)) {
      if (order.cancel(Instant.now())) {
        reservationGateway.release(order.getRequestId(), order.getSku());
        meterRegistry.counter("orders.state.changed", "state", "cancelled").increment();
      }
    }
  }

  public static class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
      super("Order not found: " + orderId);
    }
  }
}
