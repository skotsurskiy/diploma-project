package com.kts.order.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kts.order.client.dto.PaymentResponse;
import com.kts.order.domain.Order;
import com.kts.order.service.OrderPersistenceService;
import com.kts.order.web.dto.CreateOrderRequest;
import com.kts.order.web.dto.OrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Подієва (Kafka) модель обробки замовлення (Розділ 3.3.3 → 3.4.2).
 * <p>
 * Прийом: атомарно зберігаємо замовлення PENDING і подію в outbox (без виклику
 * Kafka) — клієнт одразу отримує 202. Публікацію виконує {@link OutboxRelay}.
 * Завершення: слухаємо payment-events і оновлюємо статус. Дані довговічні на
 * обох стиках (outbox у БД + журнал брокера), тож втрат немає навіть за відмови
 * Kafka чи простою payment.
 */
@Service
public class KafkaOrderService {

    public static final String ORDER_TOPIC = "order-events";
    public static final String PAYMENT_TOPIC = "payment-events";

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderService.class);

    private final OrderPersistenceService persistence;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OrderMetrics metrics;

    public KafkaOrderService(OrderPersistenceService persistence, OrderMetrics metrics) {
        this.persistence = persistence;
        this.metrics = metrics;
    }

    /**
     * Розділ 3.4.2 (Transactional Outbox). Прийом більше НЕ публікує в Kafka сам:
     * замовлення й подія пишуться атомарно в одній транзакції БД (таблиця outbox),
     * а публікацію виконує relay-шкедулер ({@link OutboxRelay}). Тому прийом
     * НІКОЛИ не залежить від доступності брокера — навіть за мертвої Kafka клієнт
     * отримує 202, а подія довговічно лежить в outbox і піде пізніше. Dual-write
     * щілини немає: успіх коміту = гарантія, що подію зрештою опублікують.
     */
    public OrderResponse accept(CreateOrderRequest request) {
        Order order = persistence.createPendingWithOutbox(request); // атомарно: order + outbox
        metrics.accepted();
        return OrderResponse.from(order);
    }

    @KafkaListener(topics = PAYMENT_TOPIC, groupId = "order-service")
    public void onPaymentCompleted(String payload) {
        PaymentCompletedEvent event = fromJson(payload, PaymentCompletedEvent.class);
        persistence.markResult(event.orderId(),
                new PaymentResponse(event.paymentId(), event.orderId(), event.status()));
        metrics.completed();
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            log.error("deserialize failed: {}", json, e);
            throw new IllegalStateException("deserialize failed", e);
        }
    }
}
