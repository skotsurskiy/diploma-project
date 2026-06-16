package com.kts.order.web.dto;

import com.kts.order.domain.Order;
import com.kts.order.domain.OrderStatus;
import java.math.BigDecimal;

public record OrderResponse(Long id, String customer, BigDecimal amount, OrderStatus status, Long paymentId) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomer(),
                order.getAmount(),
                order.getStatus(),
                order.getPaymentId());
    }
}
