package com.kts.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kts.order.client.dto.PaymentResponse;
import com.kts.order.domain.Order;
import com.kts.order.domain.OrderStatus;
import com.kts.order.domain.OutboxEvent;
import com.kts.order.kafka.KafkaOrderService;
import com.kts.order.kafka.OrderCreatedEvent;
import com.kts.order.repository.OrderRepository;
import com.kts.order.repository.OutboxRepository;
import com.kts.order.web.dto.CreateOrderRequest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Доступ до БД — спільний обмежений ресурс (пул HikariCP), який захищає Bulkhead.
 * <p>
 * Параметр {@code order.db.work-delay-ms} імітує повільний запит під
 * навантаженням: транзакція утримує з'єднання вказаний час. Це дозволяє
 * відтворити вичерпання пулу за високої конкурентності (Фаза 3.2.3).
 */
@Service
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private final long workDelayMs;

    public OrderPersistenceService(OrderRepository orderRepository,
            OutboxRepository outboxRepository,
            @Value("${order.db.work-delay-ms:0}") long workDelayMs) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.workDelayMs = workDelayMs;
    }

    @Transactional(readOnly = true)
    public Optional<Order> find(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional
    public Order createPending(CreateOrderRequest request) {
        Order order = orderRepository.save(new Order(request.customer(), request.amount()));
        simulateDbWork();
        return order;
    }

    /**
     * Розділ 3.4.2 (Transactional Outbox). Атомарно зберігає замовлення І рядок
     * outbox у ОДНІЙ локальній транзакції БД. Жодного виклику Kafka тут немає —
     * публікацію винесено в relay. Тому щілини dual-write не існує: або є і
     * замовлення, і намір його опублікувати, або немає нічого.
     */
    @Transactional
    public Order createPendingWithOutbox(CreateOrderRequest request) {
        Order order = orderRepository.save(new Order(request.customer(), request.amount()));
        OrderCreatedEvent event = new OrderCreatedEvent(order.getId(), order.getCustomer(), order.getAmount());
        outboxRepository.save(new OutboxEvent(
                KafkaOrderService.ORDER_TOPIC, String.valueOf(order.getId()), toJson(event)));
        return order;
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("serialize failed", e);
        }
    }

    @Transactional
    public Order markResult(Long orderId, PaymentResponse payment) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setPaymentId(payment.paymentId());
        order.setStatus("APPROVED".equals(payment.status()) ? OrderStatus.PAID : OrderStatus.FAILED);
        simulateDbWork();
        return orderRepository.save(order);
    }

    /** Імітація повільного запиту: з'єднання утримується протягом workDelayMs. */
    private void simulateDbWork() {
        if (workDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(workDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DB work interrupted", e);
        }
    }
}
