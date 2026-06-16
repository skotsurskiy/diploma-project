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
import org.springframework.transaction.annotation.Transactional;

/**
 * Baseline-сценарій (Фаза 3.1): синхронна обробка замовлення.
 * <p>
 * 1. Зберігаємо замовлення у статусі PENDING.
 * 2. Синхронно (блокуючи потік) звертаємось до PaymentService.
 * 3. Оновлюємо статус за результатом платежу.
 * <p>
 * Уся операція виконується в одній транзакції БД, тому з'єднання HikariCP
 * утримується протягом усього блокуючого HTTP-виклику — це навмисний дефект
 * baseline-архітектури, який аналізується далі.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository orderRepository, PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = new Order(request.customer(), request.amount());
        order = orderRepository.save(order);

        PaymentResponse payment = paymentClient.charge(
                new PaymentRequest(order.getId(), order.getAmount()));

        order.setPaymentId(payment.paymentId());
        order.setStatus("APPROVED".equals(payment.status()) ? OrderStatus.PAID : OrderStatus.FAILED);

        return OrderResponse.from(order);
    }
}
