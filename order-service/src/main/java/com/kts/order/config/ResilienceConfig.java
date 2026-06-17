package com.kts.order.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Circuit Breaker для викликів PaymentService (Фаза 3.2.2).
 * <p>
 * Логіка: payment не «падає з помилкою», а гальмує. Виклик з таймаутом 500мс
 * перетворює повільну відповідь на помилку, а Circuit Breaker після певної
 * частки невдач РОЗМИКАЄТЬСЯ і далі відмовляє миттєво (fail-fast).
 * <p>
 * Стан кільця експортується в Micrometer/Prometheus
 * ({@code resilience4j_circuitbreaker_state}) — щоб на дашборді Grafana було
 * видно момент спрацювання патерну.
 */
@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    @Bean
    public CircuitBreaker paymentCircuitBreaker(MeterRegistry meterRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50f)
                .slowCallRateThreshold(50f)
                .slowCallDurationThreshold(Duration.ofMillis(400))
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("payment");

        // Експорт метрик стану кільця в Prometheus.
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);

        cb.getEventPublisher().onStateTransition(e ->
                log.warn("CircuitBreaker '{}' перехід стану: {}",
                        e.getCircuitBreakerName(), e.getStateTransition()));
        return cb;
    }

    /**
     * Bulkhead для ізоляції доступу до БД (Фаза 3.2.3). Семафор обмежує кількість
     * одночасних звернень до БД значенням, МЕНШИМ за розмір пулу HikariCP (8 < 10),
     * тож пул ніколи не вичерпується повністю, а надлишкові запити отримують
     * миттєву відмову (fail-fast) замість очікування вільного з'єднання.
     */
    @Bean
    public Bulkhead dbBulkhead(MeterRegistry meterRegistry,
            @Value("${order.bulkhead.max-concurrent:8}") int maxConcurrent) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrent)
                .maxWaitDuration(Duration.ZERO) // не чекати — одразу відмовляти
                .build();

        BulkheadRegistry registry = BulkheadRegistry.of(config);
        Bulkhead bulkhead = registry.bulkhead("db");

        TaggedBulkheadMetrics.ofBulkheadRegistry(registry).bindTo(meterRegistry);
        return bulkhead;
    }
}
