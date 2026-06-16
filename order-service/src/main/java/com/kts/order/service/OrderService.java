package com.kts.order.service;

import com.kts.order.client.PaymentClient;
import com.kts.order.client.dto.PaymentRequest;
import com.kts.order.client.dto.PaymentResponse;
import com.kts.order.domain.Order;
import com.kts.order.domain.OrderStatus;
import com.kts.order.repository.OrderRepository;
import com.kts.order.web.dto.CreateOrderRequest;
import com.kts.order.web.dto.OrderResponse;
import org.springframework.stereotype.Service;

/**
 * Baseline-сценарій (Фаза 3.1): синхронна обробка замовлення.
 * <p>
 * 1. Зберігаємо замовлення у статусі PENDING (коротка транзакція).
 * 2. Синхронно (блокуючи ПОТІК) звертаємось до PaymentService.
 * 3. Оновлюємо статус за результатом платежу (коротка транзакція).
 * <p>
 * Важливо: HTTP-виклик до стороннього сервісу свідомо винесено ЗА МЕЖІ
 * транзакції БД. Утримувати з'єднання HikariCP протягом мережевого виклику —
 * антипатерн: пул вичерпується вже за ~50 RPS, і вузьким місцем стає БД, а не
 * потоки. Винісши виклик назовні, кожен запит тримає з'єднання лише на короткі
 * save/update, тому в 3.1 binding-обмеженням лишаються саме потоки Tomcat.
 * <p>
 * Платою за це є втрата атомарності: якщо payment впаде, замовлення лишиться у
 * статусі PENDING (узгодженість «зрештою» не гарантована). Правильне рішення —
 * патерн Transactional Outbox (запис події в ту ж БД однією транзакцією) разом
 * із Сагою — впроваджується у Розділі 3.4.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository orderRepository, PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        // Крок 1: зберігаємо замовлення (PENDING). Власна коротка транзакція —
        // з'єднання БД одразу повертається в пул.
        Order order = orderRepository.save(new Order(request.customer(), request.amount()));

        // Крок 2: блокуючий виклик платежу. Тримається лише ПОТІК Tomcat,
        // з'єднання БД у пулі вільне.
        PaymentResponse payment = paymentClient.charge(
                new PaymentRequest(order.getId(), order.getAmount()));

        // Крок 3: оновлюємо статус. Знову коротка транзакція (merge → UPDATE).
        order.setPaymentId(payment.paymentId());
        order.setStatus("APPROVED".equals(payment.status()) ? OrderStatus.PAID : OrderStatus.FAILED);
        order = orderRepository.save(order);

        return OrderResponse.from(order);
    }
}
