package com.cythzz.inventory.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cythzz.inventory.repository.StockReservationGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTests {
  @Mock
  private PurchaseOrderRepository orderRepository;

  @Mock
  private StockReservationGateway reservationGateway;

  private OrderEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new OrderEventConsumer(
        orderRepository, reservationGateway, new SimpleMeterRegistry());
  }

  @Test
  void persistsOrderAndCommitsReservation() {
    var event = new OrderCreatedEvent(
        "order-1", "request-1", "SKU-1", 1, Instant.parse("2026-01-01T00:00:00Z"));
    when(orderRepository.findByRequestId("request-1")).thenReturn(Optional.empty());

    consumer.createOrder(event);

    verify(orderRepository).save(any(PurchaseOrder.class));
    verify(reservationGateway).markCommitted("request-1");
  }

  @Test
  void ignoresAlreadyPersistedRequest() {
    var event = new OrderCreatedEvent(
        "order-2", "request-2", "SKU-2", 1, Instant.parse("2026-01-01T00:00:00Z"));
    when(orderRepository.findByRequestId("request-2"))
        .thenReturn(Optional.of(PurchaseOrder.pending(event)));

    consumer.createOrder(event);

    verify(orderRepository, never()).save(any());
    verify(reservationGateway, never()).markCommitted(any());
  }
}
