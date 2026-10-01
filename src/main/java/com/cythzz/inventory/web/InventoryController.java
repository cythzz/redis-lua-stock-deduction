package com.cythzz.inventory.web;

import com.cythzz.inventory.domain.DeductionResult;
import com.cythzz.inventory.domain.InventorySnapshot;
import com.cythzz.inventory.service.InventoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
  private final InventoryService inventoryService;

  public InventoryController(InventoryService inventoryService) {
    this.inventoryService = inventoryService;
  }

  @PostMapping("/{sku}/initialize")
  public InventorySnapshot initialize(
      @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String sku,
      @RequestParam @Min(0) long stock) {
    return inventoryService.initialize(sku, stock);
  }

  @GetMapping("/{sku}")
  public InventorySnapshot stock(@PathVariable String sku) {
    return inventoryService.stock(sku);
  }

  @PostMapping("/{sku}/deduct")
  public DeductionResult deduct(
      @PathVariable String sku,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int quantity) {
    return inventoryService.deduct(sku, quantity);
  }

  @PostMapping("/{sku}/stress-test")
  public StressTestResult stressTest(
      @PathVariable String sku,
      @RequestParam(defaultValue = "100") @Min(1) @Max(2000) int requests,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int quantity)
      throws InterruptedException {
    List<Callable<DeductionResult>> tasks = IntStream.range(0, requests)
        .mapToObj(index -> (Callable<DeductionResult>) () -> inventoryService.deduct(sku, quantity))
        .toList();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = executor.invokeAll(tasks).stream()
          .map(future -> {
            try {
              return future.get();
            } catch (Exception exception) {
              throw new IllegalStateException("高并发模拟任务执行失败", exception);
            }
          })
          .toList();
      long succeeded = results.stream()
          .filter(result -> result.status() == DeductionResult.Status.SUCCESS)
          .count();
      return new StressTestResult(
          requests, succeeded, results.size() - succeeded, inventoryService.stock(sku).stock());
    }
  }

  public record StressTestResult(
      int totalRequests, long succeeded, long rejected, long finalStock) {
  }
}
