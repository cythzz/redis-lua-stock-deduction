package com.cythzz.inventory.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisFixedWindowRateLimiter implements RequestRateLimiter {
  private static final long WINDOW_MILLIS = 1_000L;

  private final StringRedisTemplate redisTemplate;
  private final int requestsPerSecond;
  private final DefaultRedisScript<Long> rateLimitScript;

  public RedisFixedWindowRateLimiter(
      StringRedisTemplate redisTemplate,
      @Value("${app.rate-limit.requests-per-second:20}") int requestsPerSecond) {
    this.redisTemplate = redisTemplate;
    this.requestsPerSecond = requestsPerSecond;
    this.rateLimitScript = new DefaultRedisScript<>();
    this.rateLimitScript.setLocation(new ClassPathResource("scripts/fixed-window-rate-limit.lua"));
    this.rateLimitScript.setResultType(Long.class);
  }

  @Override
  public boolean allow(String clientId) {
    long window = System.currentTimeMillis() / WINDOW_MILLIS;
    Long result = redisTemplate.execute(
        rateLimitScript,
        List.of("rate-limit:order:" + clientId + ":" + window),
        Integer.toString(requestsPerSecond),
        Long.toString(WINDOW_MILLIS));
    return result != null && result >= 0L;
  }
}
