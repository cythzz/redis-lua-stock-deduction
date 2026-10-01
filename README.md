# Redis + Lua 高并发库存扣减 Demo

面向电商秒杀/库存场景的最小 Spring Boot Demo。它使用 Redis Lua 脚本完成加锁、所有者校验解锁和原子库存扣减，并提供虚拟线程并发压测接口，用于演示如何避免超卖。

## 技术栈

- Java 21
- Spring Boot 4.0.2
- Spring Data Redis
- Redis 7.4
- Maven

## 核心流程

1. `acquire-lock.lua` 使用 `SET NX PX` 获取带租约的分布式锁。
2. `deduct-stock.lua` 在 Redis 内原子完成“查询库存 + 判断 + DECRBY”。
3. `release-lock.lua` 比较随机 token，只允许锁持有者释放锁。
4. 并发请求失败时有限重试，最终返回 `LOCK_BUSY`，不会无限阻塞。

## 本地运行

```powershell
docker compose up -d
mvn spring-boot:run
```

项目遵循上游的 Java 21 要求，不需要改成 Java 17。

## 接口演示

```powershell
Invoke-RestMethod -Method Post "http://localhost:8080/api/inventory/SKU-1001/initialize?stock=50"
Invoke-RestMethod -Method Post "http://localhost:8080/api/inventory/SKU-1001/deduct?quantity=1"
Invoke-RestMethod -Method Post "http://localhost:8080/api/inventory/SKU-1001/stress-test?requests=100&quantity=1"
Invoke-RestMethod "http://localhost:8080/api/inventory/SKU-1001"
```

压测结果中的 `finalStock` 不会小于 0，成功数量不会超过初始化库存。

## 来源与改造声明

本项目基于 MIT 许可的 `alturkovic/distributed-lock` 学习改造，保留原仓库 Git 历史和 `LICENSE`。个人改造聚焦电商库存场景，重新组织为单体最小 Demo，并新增库存扣减脚本、并发压测接口、Docker Redis 环境及测试。原作者不对本改造版本提供背书或维护。

简历可描述为：**基于 Spring Boot、Redis 与 Lua 实现分布式锁和库存原子扣减，通过虚拟线程模拟高并发抢购，避免商品超卖。**
