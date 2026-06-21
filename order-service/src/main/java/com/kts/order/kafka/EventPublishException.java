package com.kts.order.kafka;

/**
 * Публікацію події не вдалося виконати ПІСЛЯ того, як замовлення вже закомічено
 * в БД (Розділ 3.4, проблема dual-write). Замовлення лишається в БД як PENDING,
 * але подія в Kafka не потрапила — замовлення «осиротіло». Клієнт отримує
 * помилку, хоча запис у БД існує: це і є неузгодженість, яку усуває
 * Transactional Outbox.
 */
public class EventPublishException extends RuntimeException {
    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
