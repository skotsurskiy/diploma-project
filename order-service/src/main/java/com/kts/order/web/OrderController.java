package com.kts.order.web;

import com.kts.order.client.PaymentUnavailableException;
import com.kts.order.service.DbBusyException;
import com.kts.order.service.OrderService;
import com.kts.order.web.dto.CreateOrderRequest;
import com.kts.order.web.dto.OrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Fail-fast: платіж недоступний (CB розімкнено або таймаут) → 503 Service
     * Unavailable. Швидка «розумна» відмова замість каскадного зависання.
     */
    @ExceptionHandler(PaymentUnavailableException.class)
    public ResponseEntity<String> handlePaymentUnavailable(PaymentUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("payment unavailable (fail-fast): " + e.getMessage());
    }

    /**
     * Fail-fast: пул БД насичений (Bulkhead переповнений) → 503. Швидка відмова
     * замість очікування вільного з'єднання й вичерпання пулу.
     */
    @ExceptionHandler(DbBusyException.class)
    public ResponseEntity<String> handleDbBusy(DbBusyException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("db busy (bulkhead full): " + e.getMessage());
    }
}
