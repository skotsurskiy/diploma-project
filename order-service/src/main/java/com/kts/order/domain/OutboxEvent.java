package com.kts.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Запис у таблиці Transactional Outbox (Розділ 3.4.2). Зберігає НАМІР
 * опублікувати подію. Вставляється в ТУ САМУ транзакцію БД, що й бізнес-сутність
 * (замовлення), тож обидва записи атомарні — щілини dual-write більше немає.
 * <p>
 * Окремий relay-шкедулер дочитує рядки з {@code published=false}, публікує їх у
 * Kafka й позначає {@code published=true}. Якщо брокер недоступний, рядок просто
 * лишається тут і буде відправлений пізніше — подія довговічна, втрат немає.
 */
@Entity
@Table(name = "outbox")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Топік Kafka, куди має піти подія. */
    @Column(nullable = false)
    private String topic;

    /** Ключ повідомлення (id агрегату — замовлення). */
    @Column(name = "msg_key", nullable = false)
    private String key;

    /** Серіалізоване тіло події (JSON). */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private boolean published = false;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    public OutboxEvent(String topic, String key, String payload) {
        this.topic = topic;
        this.key = key;
        this.payload = payload;
        this.published = false;
        this.createdAt = Instant.now();
    }
}
