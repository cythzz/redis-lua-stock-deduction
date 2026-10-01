package com.cythzz.inventory.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

class KafkaSerializationTests {

  @Test
  void serializesOrderEventIncludingTimestamp() {
    var event = new OrderCreatedEvent(
        "order-1", "request-1", "SKU-1", 2, Instant.parse("2026-01-01T00:00:00Z"));

    byte[] payload =
        new JacksonJsonSerializer<OrderCreatedEvent>().serialize("orders.created", event);

    assertThat(payload).isNotEmpty();
  }
}
