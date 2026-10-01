package com.cythzz.inventory.repository;

import java.time.Duration;

public interface InventoryGateway {
  void initialize(String sku, long stock);
  Long stock(String sku);
  boolean tryLock(String sku, String token, Duration leaseTime);
  void unlock(String sku, String token);
  long deduct(String sku, int quantity);
}
