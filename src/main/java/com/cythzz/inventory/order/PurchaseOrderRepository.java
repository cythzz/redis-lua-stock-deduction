package com.cythzz.inventory.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {
  Optional<PurchaseOrder> findByRequestId(String requestId);

  List<PurchaseOrder> findTop100ByStatusAndCreatedAtBefore(
      OrderStatus status, Instant createdBefore);
}
