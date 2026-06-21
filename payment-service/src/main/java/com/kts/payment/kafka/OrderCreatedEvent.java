package com.kts.payment.kafka;

import java.math.BigDecimal;

/** Подія «замовлення створено» — payment-service споживає її з топіка order-events. */
public record OrderCreatedEvent(Long orderId, String customer, BigDecimal amount) {
}
