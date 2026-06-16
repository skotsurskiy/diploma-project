package com.kts.payment.web.dto;

import com.kts.payment.domain.PaymentStatus;

public record PaymentResponse(Long paymentId, Long orderId, PaymentStatus status) {
}
