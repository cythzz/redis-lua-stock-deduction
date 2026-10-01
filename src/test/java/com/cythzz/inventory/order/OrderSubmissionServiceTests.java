package com.cythzz.inventory.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cythzz.inventory.domain.ReservationResult;
import com.cythzz.inventory.repository.StockReservationGateway;
import com.cythzz.inventory.service.RequestRateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSubmissionServiceTests {
  @Mock
  private StockReservationGateway reservationGateway;

  @Mock
  private OrderEventPublisher eventPublisher;

  @Mock
  private RequestRateLimiter rateLimiter;

  private OrderSubmissionService service;

  @BeforeEach
  void setUp() {
    service = new OrderSubmissionService(
        reservationGateway,
        eventPublisher,
        rateLimiter,
        new SimpleMeterRegistry(),
        Duration.ofMinutes(30));
  }

  @Test
  void reservesStockAndPublishesOrderEvent() {
    when(rateLimiter.allow("web-1")).thenReturn(true);
    when(reservationGateway.reserve(
        eq("request-001"), any(), eq("SKU-1"), eq(2), any(Duration.class)))
        .thenReturn(new ReservationResult(ReservationResult.Status.RESERVED, 8));

    OrderSubmission result = service.submit("request-001", "web-1", "SKU-1", 2);

    assertThat(result.status()).isEqualTo(OrderSubmission.Status.ACCEPTED);
    assertThat(result.remainingStock()).isEqualTo(8);
    verify(eventPublisher).publish(any(OrderCreatedEvent.class));
  }

  @Test
  void duplicateRequestDoesNotPublishAnotherMessage() {
    when(rateLimiter.allow("web-1")).thenReturn(true);
    when(reservationGateway.reserve(
        eq("request-002"), any(), eq("SKU-2"), eq(1), any(Duration.class)))
        .thenReturn(new ReservationResult(ReservationResult.Status.DUPLICATE, 9));

    OrderSubmission result = service.submit("request-002", "web-1", "SKU-2", 1);

    assertThat(result.status()).isEqualTo(OrderSubmission.Status.DUPLICATE);
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void restoresStockWhenKafkaPublishFails() {
    when(rateLimiter.allow("web-1")).thenReturn(true);
    when(reservationGateway.reserve(
        eq("request-003"), any(), eq("SKU-3"), eq(1), any(Duration.class)))
        .thenReturn(new ReservationResult(ReservationResult.Status.RESERVED, 4));
    org.mockito.Mockito.doThrow(new IllegalStateException("Kafka unavailable"))
        .when(eventPublisher).publish(any());

    OrderSubmission result = service.submit("request-003", "web-1", "SKU-3", 1);

    assertThat(result.status()).isEqualTo(OrderSubmission.Status.PUBLISH_FAILED);
    verify(reservationGateway).release("request-003", "SKU-3");
  }

  @Test
  void rejectsRequestsBeyondClientRateLimit() {
    when(rateLimiter.allow("web-2")).thenReturn(false);

    assertThatThrownBy(() -> service.submit("request-004", "web-2", "SKU-4", 1))
        .isInstanceOf(OrderSubmissionService.RateLimitExceededException.class);
    verify(reservationGateway, never()).reserve(any(), any(), any(), anyInt(), any());
  }
}
