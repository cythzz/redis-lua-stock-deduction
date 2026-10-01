package com.cythzz.inventory.web;

import com.cythzz.inventory.order.OrderService;
import com.cythzz.inventory.order.OrderSubmission;
import com.cythzz.inventory.order.OrderSubmissionService;
import com.cythzz.inventory.order.OrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
  private final OrderSubmissionService submissionService;
  private final OrderService orderService;

  public OrderController(OrderSubmissionService submissionService, OrderService orderService) {
    this.submissionService = submissionService;
    this.orderService = orderService;
  }

  @PostMapping
  public ResponseEntity<OrderSubmission> submit(
      @RequestHeader("X-Request-Id")
          @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId,
      @RequestHeader(value = "X-Client-Id", defaultValue = "anonymous") String clientId,
      @Valid @RequestBody CreateOrderRequest request) {
    OrderSubmission result =
        submissionService.submit(requestId, clientId, request.sku(), request.quantity());
    HttpStatus status = switch (result.status()) {
      case ACCEPTED -> HttpStatus.ACCEPTED;
      case DUPLICATE -> HttpStatus.OK;
      case SOLD_OUT -> HttpStatus.CONFLICT;
      case SKU_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case PUBLISH_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
    };
    return ResponseEntity.status(status).body(result);
  }

  @GetMapping("/{orderId}")
  public OrderView find(@PathVariable String orderId) {
    return orderService.find(orderId);
  }

  @PostMapping("/{orderId}/pay")
  public OrderView markPaid(@PathVariable String orderId) {
    return orderService.markPaid(orderId);
  }

  public record CreateOrderRequest(
      @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String sku,
      @Min(1) @Max(1000) int quantity) {
  }
}
