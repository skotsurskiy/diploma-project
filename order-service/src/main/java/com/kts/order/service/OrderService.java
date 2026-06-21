package com.kts.order.service;

import com.kts.order.client.PaymentClient;
import com.kts.order.client.dto.PaymentRequest;
import com.kts.order.client.dto.PaymentResponse;
import com.kts.order.domain.Order;
import com.kts.order.web.dto.CreateOrderRequest;
import com.kts.order.web.dto.OrderResponse;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Оркестрація обробки замовлення.
 * <p>
 * Доступ до БД (через {@link OrderPersistenceService}) захищено семафорним
 * Bulkhead (Фаза 3.2.3): одночасних звернень до БД не більше, ніж дозволяє
 * семафор (8 < пулу HikariCP 10), тож пул не вичерпується, а надлишок —
 * швидка відмова {@link DbBusyException} → HTTP 503.
 * <p>
 * HTTP-виклик payment лишається поза транзакцією БД і захищений Circuit Breaker
 * (3.2.2). Перемикач {@code order.bulkhead.enabled} дозволяє контрольне
 * порівняння «з bulkhead / без».
 */
@Service
public class OrderService {

    private final OrderPersistenceService persistence;
    private final PaymentClient paymentClient;
    private final Bulkhead dbBulkhead;
    private final boolean bulkheadEnabled;

    public OrderService(OrderPersistenceService persistence, PaymentClient paymentClient,
            Bulkhead dbBulkhead,
            @Value("${order.bulkhead.enabled:true}") boolean bulkheadEnabled) {
        this.persistence = persistence;
        this.paymentClient = paymentClient;
        this.dbBulkhead = dbBulkhead;
        this.bulkheadEnabled = bulkheadEnabled;
    }

    public Optional<OrderResponse> getOrder(Long id) {
        return persistence.find(id).map(OrderResponse::from);
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        // Крок 1: збереження PENDING — доступ до БД через bulkhead.
        Order order = db(() -> persistence.createPending(request));

        // Крок 2: блокуючий виклик payment (Circuit Breaker), поза транзакцією БД.
        PaymentResponse payment = paymentClient.charge(
                new PaymentRequest(order.getId(), order.getAmount()));

        // Крок 3: оновлення статусу — знову через bulkhead.
        Order finalOrder = db(() -> persistence.markResult(order.getId(), payment));

        return OrderResponse.from(finalOrder);
    }

    /** Виконує операцію з БД під захистом семафора Bulkhead (якщо ввімкнено). */
    private <T> T db(Supplier<T> operation) {
        if (!bulkheadEnabled) {
            return operation.get();
        }
        try {
            return Bulkhead.decorateSupplier(dbBulkhead, operation).get();
        } catch (BulkheadFullException e) {
            throw new DbBusyException("DB bulkhead full — fast reject", e);
        }
    }
}
