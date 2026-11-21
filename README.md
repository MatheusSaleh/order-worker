# order-worker

Consumidor **RabbitMQ** da plataforma de pedidos. Visão geral e como rodar tudo: **[order-platform](https://github.com/MatheusSaleh/order-platform)**.

![Plataforma em funcionamento](https://raw.githubusercontent.com/MatheusSaleh/order-platform/main/docs/demo.gif)

<sub>O order-worker é o nó "order-worker" do diagrama: consome a fila, faz novas tentativas e envia falhas para a DLQ.</sub>

## Fluxo de processamento

1. Consome `pedidos_fila`, um pedido por vez (`prefetch = 1`, `concurrency = 1`)
2. Atualiza o status para `PROCESSING` no MongoDB
3. Simula o processamento (tempo configurável) com falha probabilística
4. Em caso de falha: nova tentativa com backoff (`400ms × tentativa`), até 3 tentativas
5. Sucesso → `COMPLETED`. Esgotou as tentativas → `FAILED` + `AmqpRejectAndDontRequeueException`, e o RabbitMQ move a mensagem para `pedidos_fila.dlq`

Cada etapa publica um evento em `order.events` (`WORKER_PICKED`, `PROCESSING`, `RETRY`, `COMPLETED`, `DEAD_LETTERED`).

## API de controle (usada pelo simulador)

```bash
curl localhost:8082/api/worker
curl -X PUT localhost:8082/api/worker -H 'Content-Type: application/json' \
  -d '{"running": false, "failureRate": 0.3, "processingDelayMs": 800}'
```

`running: false` para o listener sem derrubar o serviço, e as mensagens se acumulam na fila.

## Rodando localmente

```bash
./mvnw spring-boot:run
```

Variáveis: `RABBITMQ_*`, `MONGODB_URI`, `WORKER_PROCESSING_DELAY_MS`, `WORKER_FAILURE_RATE`, `WORKER_MAX_ATTEMPTS`, `WORKER_RETRY_BACKOFF_MS`.
