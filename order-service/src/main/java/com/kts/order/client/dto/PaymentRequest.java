package com.kts.order.client.dto;

import java.math.BigDecimal;

public record PaymentRequest(Long orderId, BigDecimal amount) {
}
