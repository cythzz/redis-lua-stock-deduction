package com.cythzz.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cythzz.inventory.domain.DeductionResult;
import com.cythzz.inventory.repository.InventoryGateway;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTests {
  @Mock
  private InventoryGateway gateway;

  @Test
  void returnsRemainingStockAfterSuccessfulDeduction() {
    when(gateway.tryLock(eq("SKU-1"), anyString(), any(Duration.class))).thenReturn(true);
    when(gateway.deduct("SKU-1", 2)).thenReturn(8L);
    DeductionResult result = new InventoryService(gateway).deduct("SKU-1", 2);
    assertThat(result.status()).isEqualTo(DeductionResult.Status.SUCCESS);
    assertThat(result.remainingStock()).isEqualTo(8L);
    verify(gateway).unlock(eq("SKU-1"), anyString());
  }

  @Test
  void reportsSoldOutWithoutChangingStock() {
    when(gateway.tryLock(eq("SKU-2"), anyString(), any(Duration.class))).thenReturn(true);
    when(gateway.deduct("SKU-2", 3)).thenReturn(-1L);
    when(gateway.stock("SKU-2")).thenReturn(1L);
    DeductionResult result = new InventoryService(gateway).deduct("SKU-2", 3);
    assertThat(result.status()).isEqualTo(DeductionResult.Status.SOLD_OUT);
    assertThat(result.remainingStock()).isEqualTo(1L);
  }
}
