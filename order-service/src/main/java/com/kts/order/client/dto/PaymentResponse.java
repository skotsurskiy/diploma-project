package com.kts.order.client.dto;

public record PaymentResponse(Long paymentId, Long orderId, String status) {
}
