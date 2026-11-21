package com.order_worker.order_worker.order;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Atualiza apenas o status do pedido na colecao {@code orders}. O documento completo e criado pelo
 * order-server; o worker nao precisa conhecer o modelo inteiro.
 */
@Repository
@RequiredArgsConstructor
public class OrderStatusRepository {

  private static final String COLLECTION = "orders";

  private final MongoTemplate mongoTemplate;

  public void updateStatus(String orderId, String status) {
    mongoTemplate.updateFirst(
        Query.query(Criteria.where("_id").is(orderId)),
        new Update().set("status", status).set("updatedAt", Instant.now()),
        COLLECTION);
  }
}
