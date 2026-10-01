package com.cythzz.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.cythzz.inventory.order.OrderSubmission;
import com.cythzz.inventory.order.OrderSubmissionService;
import com.cythzz.inventory.order.PurchaseOrderRepository;
import com.cythzz.inventory.service.InventoryService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class OrderFlowIntegrationIT {
  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @Container
  static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
      .withDatabaseName("order_demo")
      .withUsername("demo")
      .withPassword("demo");

  @Container
  static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.0.0");

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @Autowired
  private InventoryService inventoryService;

  @Autowired
  private OrderSubmissionService submissionService;

  @Autowired
  private PurchaseOrderRepository orderRepository;

  @Test
  void createsOrderAsynchronouslyWithoutOverselling() {
    inventoryService.initialize("SKU-IT", 5);

    OrderSubmission submission =
        submissionService.submit("integration-request-001", "integration-client", "SKU-IT", 2);

    assertThat(submission.status()).isEqualTo(OrderSubmission.Status.ACCEPTED);
    await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
      assertThat(orderRepository.findByRequestId("integration-request-001")).isPresent();
    });
    assertThat(inventoryService.stock("SKU-IT").stock()).isEqualTo(3);
  }
}
