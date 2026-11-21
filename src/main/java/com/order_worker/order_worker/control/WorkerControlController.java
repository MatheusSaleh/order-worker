package com.order_worker.order_worker.control;

import com.order_worker.order_worker.order.OrderListener;
import com.order_worker.order_worker.order.OrderProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de controle usada pelo simulador do front-end. Permite "desligar" o consumo da fila para
 * demonstrar que as mensagens se acumulam no RabbitMQ sem se perder.
 */
@RestController
@RequestMapping("/api/worker")
@RequiredArgsConstructor
public class WorkerControlController {

  private final RabbitListenerEndpointRegistry registry;
  private final WorkerSettings settings;
  private final OrderProcessor processor;

  public record WorkerStatus(
      boolean running,
      double failureRate,
      long processingDelayMs,
      int maxAttempts,
      long completed,
      long failed) {}

  public record WorkerUpdate(Boolean running, Double failureRate, Long processingDelayMs) {}

  @GetMapping
  public WorkerStatus status() {
    return new WorkerStatus(
        container().isRunning(),
        settings.failureRate(),
        settings.processingDelayMs(),
        settings.maxAttempts(),
        processor.completedCount(),
        processor.failedCount());
  }

  @PutMapping
  public WorkerStatus update(@RequestBody WorkerUpdate update) {
    if (update.failureRate() != null) {
      settings.setFailureRate(update.failureRate());
    }
    if (update.processingDelayMs() != null) {
      settings.setProcessingDelayMs(update.processingDelayMs());
    }
    if (update.running() != null) {
      MessageListenerContainer container = container();
      if (update.running() && !container.isRunning()) {
        container.start();
      } else if (!update.running() && container.isRunning()) {
        // stop assincrono: termina o pedido em andamento sem bloquear a requisicao
        container.stop(() -> {});
      }
    }
    return status();
  }

  private MessageListenerContainer container() {
    return registry.getListenerContainer(OrderListener.LISTENER_ID);
  }
}
