package com.cythzz.inventory.domain;

public record DeductionResult(
    Status status,
    String sku,
    int requestedQuantity,
    long remainingStock,
    String message) {

  public enum Status {
    SUCCESS,
    SOLD_OUT,
    SKU_NOT_FOUND,
    LOCK_BUSY
  }
}
