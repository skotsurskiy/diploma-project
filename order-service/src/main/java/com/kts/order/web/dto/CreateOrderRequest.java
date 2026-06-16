package com.kts.order.web.dto;

import java.math.BigDecimal;

public record CreateOrderRequest(String customer, BigDecimal amount) {
}
