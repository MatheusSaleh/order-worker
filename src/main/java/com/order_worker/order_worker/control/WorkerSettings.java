package com.order_worker.order_worker.control;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parametros de simulacao ajustaveis em tempo de execucao pelo front-end (via order-client):
 * tempo de processamento e taxa de falha.
 */
@Component
public class WorkerSettings {

  private final AtomicLong processingDelayMs;
  private final AtomicReference<Double> failureRate;
  private final int maxAttempts;
  private final long retryBackoffMs;

  public WorkerSettings(
      @Value("${order.worker.processing-delay-ms}") long processingDelayMs,
      @Value("${order.worker.failure-rate}") double failureRate,
      @Value("${order.worker.max-attempts}") int maxAttempts,
      @Value("${order.worker.retry-backoff-ms}") long retryBackoffMs) {
    this.processingDelayMs = new AtomicLong(processingDelayMs);
    this.failureRate = new AtomicReference<>(clampRate(failureRate));
    this.maxAttempts = Math.max(1, maxAttempts);
    this.retryBackoffMs = retryBackoffMs;
  }

  public long processingDelayMs() {
    return processingDelayMs.get();
  }

  public void setProcessingDelayMs(long value) {
    processingDelayMs.set(Math.clamp(value, 0, 10_000));
  }

  public double failureRate() {
    return failureRate.get();
  }

  public void setFailureRate(double value) {
    failureRate.set(clampRate(value));
  }

  public int maxAttempts() {
    return maxAttempts;
  }

  public long retryBackoffMs() {
    return retryBackoffMs;
  }

  /** Sorteia se a tentativa atual deve falhar, de acordo com a taxa configurada. */
  public boolean shouldFail() {
    double rate = failureRate.get();
    return rate > 0 && ThreadLocalRandom.current().nextDouble() < rate;
  }

  private static double clampRate(double value) {
    return Math.clamp(value, 0.0, 1.0);
  }
}
