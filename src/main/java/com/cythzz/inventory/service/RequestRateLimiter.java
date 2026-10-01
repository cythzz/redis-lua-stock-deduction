package com.cythzz.inventory.service;

public interface RequestRateLimiter {
  boolean allow(String clientId);
}
