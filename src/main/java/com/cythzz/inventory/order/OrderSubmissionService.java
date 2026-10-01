package com.cythzz.inventory.order;

import com.cythzz.inventory.domain.ReservationResult;
import com.cythzz.inventory.repository.StockReservationGateway;
import com.cythzz.inventory.service.RequestRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderSubmissionService {
  private final StockReservationGateway reservationGateway;
  private final OrderEventPublisher eventPublisher;
  private final RequestRateLimiter rateLimiter;
  private final MeterRegistry meterRegistry;
  private final Duration reservationTtl;

  public OrderSubmissionService(
      StockReservationGateway reservationGateway,
      OrderEventPublisher eventPublisher,
      RequestRateLimiter rateLimiter,
      MeterRegistry meterRegistry,
      @Value("${app.order.reservation-ttl:30m}") Duration reservationTtl) {
    this.reservationGateway = reservationGateway;
    this.eventPublisher = eventPublisher;
    this.rateLimiter = rateLimiter;
    this.meterRegistry = meterRegistry;
    this.reservationTtl = reservationTtl;
  }

  public OrderSubmission submit(String requestId, String clientId, String sku, int quantity) {
    if (!rateLimiter.allow(clientId)) {
      meterRegistry.counter("orders.submitted", "result", "rate_limited").increment();
      throw new RateLimitExceededException(clientId);
    }

    String orderId = UUID.nameUUIDFromBytes(requestId.getBytes(StandardCharsets.UTF_8)).toString();
    ReservationResult reservation =
        reservationGateway.reserve(requestId, orderId, sku, quantity, reservationTtl);
    if (reservation.status() != ReservationResult.Status.RESERVED) {
      OrderSubmission.Status status = switch (reservation.status()) {
        case DUPLICATE -> OrderSubmission.Status.DUPLICATE;
        case SOLD_OUT -> OrderSubmission.Status.SOLD_OUT;
        case SKU_NOT_FOUND -> OrderSubmission.Status.SKU_NOT_FOUND;
        case RESERVED -> throw new IllegalStateException("Unexpected reservation status");
      };
      meterRegistry.counter("orders.submitted", "result", status.name().toLowerCase()).increment();
      return new OrderSubmission(
          status,
          orderId,
          requestId,
          sku,
          quantity,
          reservation.remainingStock(),
          status == OrderSubmission.Status.DUPLICATE ? "重复请求已被幂等拦截" : "库存预占失败");
    }

    var event = new OrderCreatedEvent(orderId, requestId, sku, quantity, Instant.now());
    try {
      eventPublisher.publish(event);
      meterRegistry.counter("orders.submitted", "result", "accepted").increment();
      return new OrderSubmission(
          OrderSubmission.Status.ACCEPTED,
          orderId,
          requestId,
          sku,
          quantity,
          reservation.remainingStock(),
          "库存已预占，订单正在异步创建");
    } catch (RuntimeException exception) {
      reservationGateway.release(requestId, sku);
      meterRegistry.counter("orders.submitted", "result", "publish_failed").increment();
      return new OrderSubmission(
          OrderSubmission.Status.PUBLISH_FAILED,
          orderId,
          requestId,
          sku,
          quantity,
          reservation.remainingStock(),
          "消息发送失败，库存已补偿");
    }
  }

  public static class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String clientId) {
      super("Too many requests from client: " + clientId);
    }
  }
}
