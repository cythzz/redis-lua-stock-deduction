# Redis + Lua 高并发订单一致性 Demo

面向电商秒杀/库存场景的轻量 Spring Boot 项目。Redis Lua 原子预占库存，Kafka 异步创建订单，MySQL 持久化订单；通过业务幂等、失败重试、死信补偿、支付超时回补和接口限流解决超卖、重复下单及消息失败问题。原有分布式锁示例仍保留，便于对比“锁 + 扣减”和“单 Lua 原子预占”两种方案。

## 技术栈

- Java 21
- Spring Boot 4.0.2
- Spring Data Redis
- Spring Kafka、MySQL、Spring Data JPA
- Redis、Kafka、MySQL、Prometheus、Grafana
- Testcontainers、k6、GitHub Actions
- Maven

## 核心流程

```text
下单请求（X-Request-Id）
  → Redis 固定窗口限流
  → Lua 原子校验幂等键并预占库存
  → Kafka 发布 OrderCreatedEvent
  → 消费者按 requestId 幂等落库 MySQL
     ├─ 成功：标记 Redis 预占已提交
     ├─ 临时失败：指数退避重试
     └─ 最终失败：进入 DLT 并执行库存补偿

未支付订单 → 定时扫描 → CAS 状态取消 → Lua 幂等回补库存
```

关键设计：

- `reserve-stock.lua` 在 Redis 内一次完成库存检查、扣减和请求幂等记录，不存在查改竞态。
- MySQL `request_id` 唯一索引和消费前查询共同防止 Kafka 重复投递产生重复订单。
- Kafka Producer 开启幂等与 `acks=all`；消费者使用 Retry Topic、指数退避和 Dead Letter Topic。
- 发布消息失败、死信消息和支付超时均走同一个幂等库存回补脚本，避免重复补偿。
- Redis Lua 固定窗口限流按客户端隔离，指标通过 Actuator/Prometheus 暴露。

## 本地运行

```powershell
docker compose up -d --build
```

`docker compose up -d --build` 会启动应用及全部依赖；需要 IDEA 调试时可只启动基础设施，再本地运行 Spring Boot：

```powershell
docker compose up -d redis mysql kafka prometheus grafana
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

订单链路：

```powershell
$headers = @{ "X-Request-Id" = "demo-request-001"; "X-Client-Id" = "web-demo" }
Invoke-RestMethod -Method Post -Headers $headers -ContentType "application/json" `
  -Body '{"sku":"SKU-1001","quantity":1}' "http://localhost:8080/api/orders"
Invoke-RestMethod "http://localhost:8080/api/orders/{orderId}"
Invoke-RestMethod -Method Post "http://localhost:8080/api/orders/{orderId}/pay"
```

运维入口：`/actuator/health`、`/actuator/prometheus`；Prometheus 和 Grafana 默认端口分别是 `9090`、`3000`。

## 测试与压测

```powershell
mvn test
mvn -Pintegration verify
k6 run performance/order-spike.js
```

集成测试通过 Testcontainers 启动 Redis、MySQL 和 Kafka。未安装 Docker 时该集成测试会跳过。压测结果模板位于 `performance/RESULTS.md`，只记录实际执行数据，不填写虚构 QPS。

压测结果中的 `finalStock` 不会小于 0，成功数量不会超过初始化库存。

## 来源与改造声明

本项目基于 MIT 许可的 `alturkovic/distributed-lock` 学习改造，保留原仓库 Git 历史和 `LICENSE`。个人改造聚焦电商订单一致性，新增 Redis Lua 库存预占、Kafka 订单事件、MySQL 订单模型、幂等消费、重试/DLT、超时补偿、限流、可观测性、Testcontainers 和 k6。原作者不对本改造版本提供背书或维护。

简历可描述为：**基于 Redis Lua、MySQL 和 Kafka 实现高并发库存预占及异步订单流程，通过业务幂等、失败重试、死信队列和超时库存补偿解决重复消费、超卖及消息处理失败问题，并使用 Testcontainers 与 k6 完成集成测试和并发验证。**
