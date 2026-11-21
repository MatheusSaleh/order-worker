package com.order_worker.order_worker.event;

import java.util.Map;

/** Evento de ciclo de vida de um pedido publicado no exchange {@code order.events}. */
public record OrderEvent(
    String orderId,
    String stage,
    String service,
    long timestamp,
    String message,
    Map<String, Object> data) {}
