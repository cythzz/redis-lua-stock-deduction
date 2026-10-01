package com.cythzz.inventory.order;

public interface OrderEventPublisher {
  void publish(OrderCreatedEvent event);
}
