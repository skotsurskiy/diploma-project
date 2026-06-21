package com.kts.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kts.payment.service.PaymentService;
import com.kts.payment.web.dto.PaymentRequest;
import com.kts.payment.web.dto.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Подієва обробка платежу (Розділ 3.3.3). Споживає OrderCreatedEvent із топіка
 * order-events, обробляє платіж (з тією ж імітацією затримки) і публікує
 * PaymentCompletedEvent у payment-events.
 * <p>
 * Якщо цей сервіс зупинити («сон»), споживання припиняється, а події в
 * order-events накопичуються в брокері (зростає consumer lag). Після запуску
 * сервіс продовжує з останнього зафіксованого зміщення й вичерпує весь backlog —
 * жодне замовлення не втрачається.
 */
@Service
public class PaymentKafkaService {

    public static final String ORDER_TOPIC = "order-events";
    public static final String PAYMENT_TOPIC = "payment-events";

    private static final Logger log = LoggerFactory.getLogger(PaymentKafkaService.class);

    private final PaymentService paymentService;
    @SuppressWarnings("rawtypes")
    private final KafkaTemplate kafka;
    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("rawtypes")
    public PaymentKafkaService(PaymentService paymentService, KafkaTemplate kafka) {
        this.paymentService = paymentService;
        this.kafka = kafka;
    }

    @KafkaListener(topics = ORDER_TOPIC, groupId = "payment-service")
    public void onOrderCreated(String payload) {
        OrderCreatedEvent event = fromJson(payload, OrderCreatedEvent.class);

        PaymentResponse result = paymentService.process(new PaymentRequest(event.orderId(), event.amount()));

        PaymentCompletedEvent completed = new PaymentCompletedEvent(
                event.orderId(), result.paymentId(), result.status().name());
        kafka.send(PAYMENT_TOPIC, String.valueOf(event.orderId()), toJson(completed));
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("serialize failed", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            log.error("deserialize failed: {}", json, e);
            throw new IllegalStateException("deserialize failed", e);
        }
    }
}
