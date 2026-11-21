package com.order_worker.order_worker.order;

import com.order_worker.order_worker.control.WorkerSettings;
import com.order_worker.order_worker.event.OrderEventPublisher;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Regra de processamento de um pedido.
 *
 * <p>Cada tentativa simula um trabalho (estoque, pagamento...) que pode falhar conforme a taxa
 * configurada. Apos {@code maxAttempts} falhas, a mensagem e rejeitada sem requeue e o RabbitMQ a
 * envia para a dead letter queue.
 */
@Slf4j
@Service
public class OrderProcessor {

  /** Abstrai o Thread.sleep para permitir testes rapidos. */
  @FunctionalInterface
  public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }

  private final OrderStatusRepository statusRepository;
  private final OrderEventPublisher events;
  private final WorkerSettings settings;
  private final Sleeper sleeper;

  private final AtomicLong completed = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();

  @Autowired
  public OrderProcessor(
      OrderStatusRepository statusRepository,
      OrderEventPublisher events,
      WorkerSettings settings) {
    this(statusRepository, events, settings, Thread::sleep);
  }

  OrderProcessor(
      OrderStatusRepository statusRepository,
      OrderEventPublisher events,
      WorkerSettings settings,
      Sleeper sleeper) {
    this.statusRepository = statusRepository;
    this.events = events;
    this.settings = settings;
    this.sleeper = sleeper;
  }

  public void process(OrderQueueMessage order) {
    String orderId = order.orderId();
    long startedAt = System.currentTimeMillis();
    int maxAttempts = settings.maxAttempts();

    events.publish(
        orderId,
        "WORKER_PICKED",
        "order-worker retirou o pedido da fila",
        Map.of("customerName", order.customerName(), "items", order.items().size()));

    statusRepository.updateStatus(orderId, "PROCESSING");
    events.publish(
        orderId,
        "PROCESSING",
        "Processando pedido: reservando estoque e validando pagamento",
        Map.of("delayMs", settings.processingDelayMs(), "status", "PROCESSING"));

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      pause(settings.processingDelayMs());

      if (!settings.shouldFail()) {
        statusRepository.updateStatus(orderId, "COMPLETED");
        completed.incrementAndGet();
        events.publish(
            orderId,
            "COMPLETED",
            "Pedido processado e marcado como COMPLETED no MongoDB",
            Map.of(
                "attempt", attempt,
                "status", "COMPLETED",
                "totalAmount", order.totalAmount(),
                "processingMs", System.currentTimeMillis() - startedAt));
        log.info("Pedido {} concluido na tentativa {}", orderId, attempt);
        return;
      }

      log.warn("Falha simulada no pedido {} (tentativa {}/{})", orderId, attempt, maxAttempts);
      if (attempt < maxAttempts) {
        long backoff = settings.retryBackoffMs() * attempt;
        events.publish(
            orderId,
            "RETRY",
            "Falha no processamento, nova tentativa em " + backoff + " ms",
            Map.of("attempt", attempt, "maxAttempts", maxAttempts, "backoffMs", backoff));
        pause(backoff);
      }
    }

    statusRepository.updateStatus(orderId, "FAILED");
    failed.incrementAndGet();
    events.publish(
        orderId,
        "DEAD_LETTERED",
        "Pedido falhou " + maxAttempts + " vezes e foi enviado para a DLQ",
        Map.of("attempts", maxAttempts, "status", "FAILED", "queue", "pedidos_fila.dlq"));

    throw new AmqpRejectAndDontRequeueException(
        "Pedido " + orderId + " falhou apos " + maxAttempts + " tentativas");
  }

  public long completedCount() {
    return completed.get();
  }

  public long failedCount() {
    return failed.get();
  }

  private void pause(long millis) {
    if (millis <= 0) {
      return;
    }
    try {
      sleeper.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
