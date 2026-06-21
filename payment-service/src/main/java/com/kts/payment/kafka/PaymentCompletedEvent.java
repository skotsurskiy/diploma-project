package com.kts.payment.kafka;

/** Подія «платіж оброблено» — payment-service публікує її в топік payment-events. */
public record PaymentCompletedEvent(Long orderId, Long paymentId, String status) {
}
