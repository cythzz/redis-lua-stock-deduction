package com.cythzz.inventory.order;

import com.cythzz.inventory.repository.StockReservationGateway;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderEventConsumer {
  private final PurchaseOrderRepository orderRepository;
  private final StockReservationGateway reservationGateway;
  private final MeterRegistry meterRegistry;

  public OrderEventConsumer(
      PurchaseOrderRepository orderRepository,
      StockReservationGateway reservationGateway,
      MeterRegistry meterRegistry) {
    this.orderRepository = orderRepository;
    this.reservationGateway = reservationGateway;
    this.meterRegistry = meterRegistry;
  }

  @RetryableTopic(
      attempts = "4",
      backOff = @BackOff(delay = 1_000L, multiplier = 2.0, maxDelay = 8_000L),
      dltTopicSuffix = "-dlt")
  @KafkaListener(topics = "${app.kafka.order-topic:orders.created}")
  @Transactional
  public void createOrder(OrderCreatedEvent event) {
    if (orderRepository.findByRequestId(event.requestId()).isPresent()) {
      meterRegistry.counter("orders.consumed", "result", "duplicate").increment();
      return;
    }
    orderRepository.save(PurchaseOrder.pending(event));
    reservationGateway.markCommitted(event.requestId());
    meterRegistry.counter("orders.consumed", "result", "created").increment();
  }

  @DltHandler
  public void compensateDeadLetter(OrderCreatedEvent event) {
    reservationGateway.release(event.requestId(), event.sku());
    meterRegistry.counter("orders.consumed", "result", "dead_letter_compensated").increment();
  }
}
