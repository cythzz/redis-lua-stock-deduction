package com.cythzz.inventory.order;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaOrderEventPublisher implements OrderEventPublisher {
  private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
  private final String orderTopic;

  public KafkaOrderEventPublisher(
      KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate,
      @Value("${app.kafka.order-topic:orders.created}") String orderTopic) {
    this.kafkaTemplate = kafkaTemplate;
    this.orderTopic = orderTopic;
  }

  @Override
  public void publish(OrderCreatedEvent event) {
    try {
      kafkaTemplate.send(orderTopic, event.orderId(), event).get(5, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new OrderPublishException("Kafka order event publish failed", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new OrderPublishException("Kafka order event publish failed", exception);
    }
  }

  public static class OrderPublishException extends RuntimeException {
    public OrderPublishException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
