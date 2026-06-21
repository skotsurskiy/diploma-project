package com.kts.order.web;

import com.kts.order.client.PaymentUnavailableException;
import com.kts.order.kafka.EventPublishException;
import com.kts.order.kafka.KafkaOrderService;
import com.kts.order.service.DbBusyException;
import com.kts.order.service.OrderService;
import com.kts.order.web.dto.CreateOrderRequest;
import com.kts.order.web.dto.OrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final KafkaOrderService kafkaOrderService;

    public OrderController(OrderService orderService, KafkaOrderService kafkaOrderService) {
        this.orderService = orderService;
        this.kafkaOrderService = kafkaOrderService;
    }

    /**
     * Спосіб 1 — СИНХРОННИЙ (Розділ 3.3). Клієнт чекає на повну обробку (включно
     * з платежем) і одразу отримує фінальний статус (201 з PAID/FAILED) або
     * швидку відмову (503). Request-response: результат відомий у відповіді.
     */
    @PostMapping("/sync")
    public ResponseEntity<OrderResponse> createSync(@RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Псевдонім /orders → синхронний спосіб (зворотна сумісність із тестами). */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody CreateOrderRequest request) {
        return createSync(request);
    }

    /**
     * Спосіб 3 — ПОДІЄВИЙ (Kafka, Розділ 3.3.3). Зберігаємо PENDING, публікуємо
     * подію й одразу повертаємо 202. Платіж обробляється подієво; статус —
     * через GET /orders/{id}. Дані довговічні: payment може «спати», події
     * накопичаться й опрацюються після пробудження.
     */
    @PostMapping("/kafka")
    public ResponseEntity<OrderResponse> createKafka(@RequestBody CreateOrderRequest request) {
        OrderResponse response = kafkaOrderService.accept(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /** Поточний стан замовлення — для опитування результату async/Kafka-способів. */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable Long id) {
        return orderService.getOrder(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
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

    /**
     * Розділ 3.4 (проблема dual-write): подію не вдалося опублікувати ПІСЛЯ
     * коміту замовлення → 500. Замовлення лишилось у БД як PENDING (осиротіло),
     * хоча клієнт отримав помилку — це і є неузгодженість без Transactional Outbox.
     */
    @ExceptionHandler(EventPublishException.class)
    public ResponseEntity<String> handleEventPublish(EventPublishException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("event publish failed (order committed but orphaned): " + e.getMessage());
    }
}
