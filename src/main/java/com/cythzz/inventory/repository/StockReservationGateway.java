package com.cythzz.inventory.repository;

import com.cythzz.inventory.domain.ReservationResult;
import java.time.Duration;

public interface StockReservationGateway {
  ReservationResult reserve(
      String requestId, String orderId, String sku, int quantity, Duration reservationTtl);

  void markCommitted(String requestId);

  boolean release(String requestId, String sku);
}
