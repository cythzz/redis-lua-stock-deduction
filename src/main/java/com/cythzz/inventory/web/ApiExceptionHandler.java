package com.cythzz.inventory.web;

import com.cythzz.inventory.service.InventoryService.SkuNotFoundException;
import com.cythzz.inventory.order.OrderService.OrderNotFoundException;
import com.cythzz.inventory.order.OrderSubmissionService.RateLimitExceededException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(SkuNotFoundException.class)
  public Map<String, String> handleSkuNotFound(SkuNotFoundException exception) {
    return Map.of("error", exception.getMessage());
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(OrderNotFoundException.class)
  public Map<String, String> handleOrderNotFound(OrderNotFoundException exception) {
    return Map.of("error", exception.getMessage());
  }

  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  @ExceptionHandler(RateLimitExceededException.class)
  public Map<String, String> handleRateLimit(RateLimitExceededException exception) {
    return Map.of("error", exception.getMessage());
  }
}
