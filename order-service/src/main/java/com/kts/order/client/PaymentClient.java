package com.kts.order.client;

import com.kts.order.client.dto.PaymentRequest;
import com.kts.order.client.dto.PaymentResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Синхронний блокуючий виклик PaymentService (Фаза 3.1).
 * Потік, що обслуговує HTTP-запит замовлення, блокується тут до отримання
 * відповіді платіжного сервісу.
 */
@Component
public class PaymentClient {

    private final RestClient paymentRestClient;

    public PaymentClient(RestClient paymentRestClient) {
        this.paymentRestClient = paymentRestClient;
    }

    public PaymentResponse charge(PaymentRequest request) {
        return paymentRestClient.post()
                .uri("/payments")
                .body(request)
                .retrieve()
                .body(PaymentResponse.class);
    }
}
