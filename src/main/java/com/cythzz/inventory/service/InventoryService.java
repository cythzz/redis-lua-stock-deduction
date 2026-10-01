package com.cythzz.inventory.service;

import com.cythzz.inventory.domain.DeductionResult;
import com.cythzz.inventory.domain.InventorySnapshot;
import com.cythzz.inventory.repository.InventoryGateway;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {
  private static final Duration LOCK_LEASE = Duration.ofSeconds(3);
  private static final int MAX_LOCK_ATTEMPTS = 10;

  private final InventoryGateway gateway;

  public InventoryService(InventoryGateway gateway) {
    this.gateway = gateway;
  }

  public InventorySnapshot initialize(String sku, long stock) {
    gateway.initialize(sku, stock);
    return new InventorySnapshot(sku, stock);
  }

  public InventorySnapshot stock(String sku) {
    Long stock = gateway.stock(sku);
    if (stock == null) {
      throw new SkuNotFoundException(sku);
    }
    return new InventorySnapshot(sku, stock);
  }

  public DeductionResult deduct(String sku, int quantity) {
    String token = UUID.randomUUID().toString();
    for (int attempt = 0; attempt < MAX_LOCK_ATTEMPTS; attempt++) {
      if (gateway.tryLock(sku, token, LOCK_LEASE)) {
        try {
          return mapResult(sku, quantity, gateway.deduct(sku, quantity));
        } finally {
          gateway.unlock(sku, token);
        }
      }
      pauseBeforeRetry(attempt);
    }
    Long remaining = gateway.stock(sku);
    return new DeductionResult(
        DeductionResult.Status.LOCK_BUSY,
        sku,
        quantity,
        remaining == null ? -1 : remaining,
        "库存正在被其他请求处理，请稍后重试");
  }

  private DeductionResult mapResult(String sku, int quantity, long remaining) {
    if (remaining >= 0) {
      return new DeductionResult(
          DeductionResult.Status.SUCCESS, sku, quantity, remaining, "扣减成功");
    }
    if (remaining == -1) {
      Long current = gateway.stock(sku);
      return new DeductionResult(
          DeductionResult.Status.SOLD_OUT,
          sku,
          quantity,
          current == null ? 0 : current,
          "库存不足，未发生扣减");
    }
    return new DeductionResult(
        DeductionResult.Status.SKU_NOT_FOUND, sku, quantity, -1, "SKU 尚未初始化");
  }

  private static void pauseBeforeRetry(int attempt) {
    try {
      Thread.sleep(10L + attempt * 5L);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  public static class SkuNotFoundException extends RuntimeException {
    public SkuNotFoundException(String sku) {
      super("SKU not found: " + sku);
    }
  }
}
