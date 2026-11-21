package com.order_worker.order_worker.order;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderListener {

  /** Id usado pelo {@code WorkerControlService} para pausar/retomar o consumo. */
  public static final String LISTENER_ID = "order-listener";

  private final OrderProcessor processor;

  @RabbitListener(id = LISTENER_ID, queues = "${order.queue}")
  public void onOrder(OrderQueueMessage order) {
    processor.process(order);
  }
}
