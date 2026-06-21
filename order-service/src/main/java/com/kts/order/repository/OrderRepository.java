package com.kts.order.repository;

import com.kts.order.domain.Order;
import com.kts.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Кількість замовлень у заданому статусі — джерело істини з БД (не з пам'яті).
     * Для Розділу 3.4: зростання PENDING, що НЕ дренажується після відновлення
     * Kafka, викриває «осиротілі» замовлення (подію втрачено через dual-write).
     */
    long countByStatus(OrderStatus status);
}
