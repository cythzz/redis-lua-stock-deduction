package com.cythzz.inventory.domain;

public record ReservationResult(Status status, long remainingStock) {
  public enum Status {
    RESERVED,
    DUPLICATE,
    SOLD_OUT,
    SKU_NOT_FOUND
  }
}
