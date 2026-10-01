package com.cythzz.inventory.repository;

import com.cythzz.inventory.domain.ReservationResult;
import java.time.Duration;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisStockReservationGateway implements StockReservationGateway {
  private static final String STOCK_PREFIX = "inventory:stock:";
  private static final String RESERVATION_PREFIX = "inventory:reservation:";

  private final StringRedisTemplate redisTemplate;
  private final DefaultRedisScript<Long> reserveScript;
  private final DefaultRedisScript<Long> commitScript;
  private final DefaultRedisScript<Long> releaseScript;

  public RedisStockReservationGateway(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
    this.reserveScript = script("scripts/reserve-stock.lua");
    this.commitScript = script("scripts/mark-reservation-committed.lua");
    this.releaseScript = script("scripts/release-reservation.lua");
  }

  @Override
  public ReservationResult reserve(
      String requestId, String orderId, String sku, int quantity, Duration reservationTtl) {
    Long result = redisTemplate.execute(
        reserveScript,
        List.of(stockKey(sku), reservationKey(requestId)),
        orderId,
        Integer.toString(quantity),
        Long.toString(reservationTtl.toMillis()));
    long code = result == null ? -2L : result;
    if (code >= 0) {
      return new ReservationResult(ReservationResult.Status.RESERVED, code);
    }
    if (code == -4L) {
      String stockValue = redisTemplate.opsForValue().get(stockKey(sku));
      Long stock = stockValue == null ? null : Long.parseLong(stockValue);
      return new ReservationResult(
          ReservationResult.Status.DUPLICATE, stock == null ? -1L : stock);
    }
    return new ReservationResult(
        code == -1L ? ReservationResult.Status.SOLD_OUT : ReservationResult.Status.SKU_NOT_FOUND,
        code == -1L ? currentStock(sku) : -1L);
  }

  @Override
  public void markCommitted(String requestId) {
    redisTemplate.execute(commitScript, List.of(reservationKey(requestId)));
  }

  @Override
  public boolean release(String requestId, String sku) {
    Long result = redisTemplate.execute(
        releaseScript, List.of(stockKey(sku), reservationKey(requestId)));
    return Long.valueOf(1L).equals(result);
  }

  private long currentStock(String sku) {
    String value = redisTemplate.opsForValue().get(stockKey(sku));
    return value == null ? 0L : Long.parseLong(value);
  }

  private static DefaultRedisScript<Long> script(String path) {
    var script = new DefaultRedisScript<Long>();
    script.setLocation(new ClassPathResource(path));
    script.setResultType(Long.class);
    return script;
  }

  private static String stockKey(String sku) {
    return STOCK_PREFIX + sku;
  }

  private static String reservationKey(String requestId) {
    return RESERVATION_PREFIX + requestId;
  }
}
