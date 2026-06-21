package com.kts.order.kafka;

import java.math.BigDecimal;

/** Подія «замовлення створено» — публікується order-service у топік order-events. */
public record OrderCreatedEvent(Long orderId, String customer, BigDecimal amount) {
}
