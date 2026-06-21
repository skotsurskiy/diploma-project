package com.kts.order.kafka;

import com.kts.order.domain.OutboxEvent;
import com.kts.order.repository.OutboxRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Relay для Transactional Outbox (Розділ 3.4.2). Шкедулер періодично дочитує
 * неопубліковані рядки outbox і публікує їх у Kafka, після чого позначає
 * {@code published=true}.
 * <p>
 * Гарантія: подію спершу відправляємо в брокер і лише потім фіксуємо публікацію.
 * Якщо брокер недоступний — {@code send().get()} падає, рядок лишається
 * неопублікованим і буде відправлений на наступному тіку (ретрай); нічого не
 * втрачається. Якщо процес упаде між успішним send і фіксацією — після рестарту
 * подію відправлять повторно (семантика at-least-once), тож споживач має бути
 * ідемпотентним. Партію обробляємо строго по порядку (FIFO) і за першого збою
 * зупиняємось, щоб зберегти послідовність і не молотити мертвий брокер.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    @SuppressWarnings("rawtypes")
    private final KafkaTemplate kafka;
    private final OrderMetrics metrics;

    @SuppressWarnings("rawtypes")
    public OutboxRelay(OutboxRepository outboxRepository, KafkaTemplate kafka, OrderMetrics metrics) {
        this.outboxRepository = outboxRepository;
        this.kafka = kafka;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelay = 300)
    @SuppressWarnings("unchecked")
    public void flush() {
        List<OutboxEvent> batch = outboxRepository.findTop200ByPublishedFalseOrderByIdAsc();
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent e : batch) {
            try {
                kafka.send(e.getTopic(), e.getKey(), e.getPayload()).get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                // Брокер недоступний — лишаємо рядок неопублікованим, ретрай на наступному тіку.
                log.warn("outbox relay: publish failed (will retry), pending stays durable: {}",
                        ex.getMessage());
                break;
            }
            e.setPublished(true);
            e.setPublishedAt(Instant.now());
            outboxRepository.save(e);
            metrics.relayPublished();
        }
    }
}
