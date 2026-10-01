package com.cythzz.inventory.repository;

import java.time.Duration;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisInventoryGateway implements InventoryGateway {
  private static final String STOCK_PREFIX = "inventory:stock:";
  private static final String LOCK_PREFIX = "inventory:lock:";

  private final StringRedisTemplate redisTemplate;
  private final DefaultRedisScript<Long> lockScript;
  private final DefaultRedisScript<Long> unlockScript;
  private final DefaultRedisScript<Long> deductScript;

  public RedisInventoryGateway(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
    this.lockScript = script("scripts/acquire-lock.lua");
    this.unlockScript = script("scripts/release-lock.lua");
    this.deductScript = script("scripts/deduct-stock.lua");
  }

  private static DefaultRedisScript<Long> script(String path) {
    var script = new DefaultRedisScript<Long>();
    script.setLocation(new ClassPathResource(path));
    script.setResultType(Long.class);
    return script;
  }

  @Override
  public void initialize(String sku, long stock) {
    redisTemplate.opsForValue().set(stockKey(sku), Long.toString(stock));
  }

  @Override
  public Long stock(String sku) {
    String value = redisTemplate.opsForValue().get(stockKey(sku));
    return value == null ? null : Long.parseLong(value);
  }

  @Override
  public boolean tryLock(String sku, String token, Duration leaseTime) {
    Long result = redisTemplate.execute(
        lockScript, List.of(lockKey(sku)), token, Long.toString(leaseTime.toMillis()));
    return Long.valueOf(1L).equals(result);
  }

  @Override
  public void unlock(String sku, String token) {
    redisTemplate.execute(unlockScript, List.of(lockKey(sku)), token);
  }

  @Override
  public long deduct(String sku, int quantity) {
    Long result = redisTemplate.execute(
        deductScript, List.of(stockKey(sku)), Integer.toString(quantity));
    return result == null ? -3 : result;
  }

  private static String stockKey(String sku) {
    return STOCK_PREFIX + sku;
  }

  private static String lockKey(String sku) {
    return LOCK_PREFIX + sku;
  }
}
