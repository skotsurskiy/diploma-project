package com.kts.order.service;

import com.kts.order.client.dto.PaymentResponse;
import com.kts.order.domain.Order;
import com.kts.order.domain.OrderStatus;
import com.kts.order.repository.OrderRepository;
import com.kts.order.web.dto.CreateOrderRequest;
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
    private final long workDelayMs;

    public OrderPersistenceService(OrderRepository orderRepository,
            @Value("${order.db.work-delay-ms:0}") long workDelayMs) {
        this.orderRepository = orderRepository;
        this.workDelayMs = workDelayMs;
    }

    @Transactional
    public Order createPending(CreateOrderRequest request) {
        Order order = orderRepository.save(new Order(request.customer(), request.amount()));
        simulateDbWork();
        return order;
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
