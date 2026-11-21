package com.order_worker.order_worker.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mesma topologia declarada pelo order-server (os argumentos precisam ser identicos), assim o
 * worker pode subir antes ou depois do servidor.
 */
@Configuration
public class RabbitConfig {

  public static final String EVENTS_EXCHANGE = "order.events";

  @Bean
  MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
    // O tipo do payload e inferido pelo parametro do @RabbitListener,
    // entao o worker nao depende das classes do order-server.
    return new JacksonJsonMessageConverter(jsonMapper);
  }

  @Bean
  Queue ordersQueue(@Value("${order.queue}") String queue) {
    return QueueBuilder.durable(queue)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", queue + ".dlq")
        .build();
  }

  @Bean
  Queue ordersDeadLetterQueue(@Value("${order.queue}") String queue) {
    return QueueBuilder.durable(queue + ".dlq").build();
  }

  @Bean
  TopicExchange orderEventsExchange() {
    return new TopicExchange(EVENTS_EXCHANGE, true, false);
  }
}
