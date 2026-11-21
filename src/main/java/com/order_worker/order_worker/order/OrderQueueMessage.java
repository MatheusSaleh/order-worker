package com.order_worker.order_worker.order;

import java.time.Instant;
import java.util.List;

/** Mensagem lida da fila {@code pedidos_fila} (JSON publicado pelo order-server). */
public record OrderQueueMessage(
    String orderId,
    String customerName,
    List<Item> items,
    double totalAmount,
    Instant createdAt) {

  public record Item(String productId, String productName, int quantity, double price) {}
}
