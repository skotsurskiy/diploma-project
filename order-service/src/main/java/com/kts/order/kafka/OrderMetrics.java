package com.kts.order.kafka;

import com.kts.order.domain.OrderStatus;
import com.kts.order.repository.OrderRepository;
import com.kts.order.repository.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Метрики для порівняння моделей взаємодії (Розділ 3.3) і демонстрації
 * довговічності Kafka:
 *  - orders_accepted_total{method} — прийнято запитів;
 *  - orders_completed_total{method} — доведено до фінального статусу;
 *  - orders_pending — у польоті за версією ПАМ'ЯТІ (прийнято, але ще не завершено).
 * Під час «сну» payment accepted росте, completed завмирає, pending зростає;
 * після пробудження completed наздоганяє, pending повертається до 0 — 0 втрат.
 * <p>
 * Розділ 3.4 (проблема dual-write, відсутній Transactional Outbox):
 *  - orders_publish_failed_total — публікацію події не вдалося виконати ПІСЛЯ
 *    коміту замовлення (брокер недоступний) → замовлення «осиротіло»;
 *  - orders_db_pending — РЕАЛЬНА кількість PENDING у БД (джерело істини). На
 *    відміну від orders_pending (пам'ять), цей лічильник бачить осиротілі
 *    замовлення: за відсутності Outbox він НЕ дренажується навіть після
 *    відновлення Kafka, бо подію втрачено безповоротно.
 */
@Component
public class OrderMetrics {

    private final Counter accepted;
    private final Counter completed;
    private final Counter publishFailed;
    private final Counter relayPublished;
    private final AtomicLong pending = new AtomicLong(0);

    public OrderMetrics(MeterRegistry registry, OrderRepository orderRepository,
            OutboxRepository outboxRepository) {
        this.accepted = Counter.builder("orders.accepted").tag("method", "kafka").register(registry);
        this.completed = Counter.builder("orders.completed").tag("method", "kafka").register(registry);
        this.publishFailed = Counter.builder("orders.publish.failed").tag("method", "kafka").register(registry);
        // Розділ 3.4.2: подій, опублікованих relay-ем з outbox у Kafka.
        this.relayPublished = Counter.builder("outbox.published").register(registry);
        Gauge.builder("orders.pending", pending, AtomicLong::get).register(registry);
        // Значення береться з БД на кожному scrape — джерело істини про осиротілі замовлення.
        Gauge.builder("orders.db.pending", orderRepository, r -> r.countByStatus(OrderStatus.PENDING))
                .register(registry);
        // Розділ 3.4.2: довговічний буфер outbox — неопубліковані події чекають у БД.
        // За відмови Kafka росте, після відновлення relay дренажує його до 0 (нічого не втрачено).
        Gauge.builder("outbox.pending", outboxRepository, OutboxRepository::countByPublishedFalse)
                .register(registry);
    }

    public void accepted() {
        accepted.increment();
        pending.incrementAndGet();
    }

    public void completed() {
        completed.increment();
        pending.decrementAndGet();
    }

    /** Подію не опубліковано після коміту замовлення — dual-write втрата (3.4). */
    public void publishFailed() {
        publishFailed.increment();
    }

    /** Relay успішно опублікував подію з outbox у Kafka (3.4.2). */
    public void relayPublished() {
        relayPublished.increment();
    }
}
