package com.kts.order.kafka;

/** Подія «платіж оброблено» — публікується payment-service у топік payment-events. */
public record PaymentCompletedEvent(Long orderId, Long paymentId, String status) {
}
