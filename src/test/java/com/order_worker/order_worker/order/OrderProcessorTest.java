package com.order_worker.order_worker.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.order_worker.order_worker.control.WorkerSettings;
import com.order_worker.order_worker.event.OrderEventPublisher;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

class OrderProcessorTest {

  private static final OrderQueueMessage ORDER =
      new OrderQueueMessage(
          "pedido-1",
          "Ana",
          List.of(new OrderQueueMessage.Item("p1", "Teclado", 1, 100.0)),
          100.0,
          Instant.now());

  private OrderStatusRepository statusRepository;
  private OrderEventPublisher events;

  @BeforeEach
  void setUp() {
    statusRepository = mock(OrderStatusRepository.class);
    events = mock(OrderEventPublisher.class);
  }

  private OrderProcessor processor(double failureRate) {
    WorkerSettings settings = new WorkerSettings(0, failureRate, 3, 0);
    return new OrderProcessor(statusRepository, events, settings, millis -> {});
  }

  @Test
  void concluiPedidoQuandoNaoHaFalhas() {
    OrderProcessor processor = processor(0.0);

    processor.process(ORDER);

    InOrder order = inOrder(statusRepository, events);
    order.verify(events).publish(eq("pedido-1"), eq("WORKER_PICKED"), anyString(), anyMap());
    order.verify(statusRepository).updateStatus("pedido-1", "PROCESSING");
    order.verify(statusRepository).updateStatus("pedido-1", "COMPLETED");
    order.verify(events).publish(eq("pedido-1"), eq("COMPLETED"), anyString(), anyMap());
    verify(events, never()).publish(any(), eq("RETRY"), any(), any());
    assertThat(processor.completedCount()).isEqualTo(1);
  }

  @Test
  void enviaParaDlqAposEsgotarTentativas() {
    OrderProcessor processor = processor(1.0);

    assertThatThrownBy(() -> processor.process(ORDER))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);

    verify(events, times(2)).publish(eq("pedido-1"), eq("RETRY"), anyString(), anyMap());
    verify(events).publish(eq("pedido-1"), eq("DEAD_LETTERED"), anyString(), anyMap());
    verify(statusRepository).updateStatus("pedido-1", "FAILED");
    verify(statusRepository, never()).updateStatus("pedido-1", "COMPLETED");
    assertThat(processor.failedCount()).isEqualTo(1);
  }
}
