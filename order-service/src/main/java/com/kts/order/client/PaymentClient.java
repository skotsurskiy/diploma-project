package com.kts.order.client;

import com.kts.order.client.dto.PaymentRequest;
import com.kts.order.client.dto.PaymentResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Виклик PaymentService, захищений Circuit Breaker (Фаза 3.2.2).
 * <p>
 * Якщо кільце розімкнено — виклик навіть не йде в мережу, одразу
 * {@link CallNotPermittedException} → швидка відмова. Якщо кільце замкнене, але
 * виклик упав/перевищив таймаут — помилка фіксується breaker'ом для статистики.
 * В обох випадках назовні віддаємо {@link PaymentUnavailableException}.
 */
@Component
public class PaymentClient {

    private final RestClient paymentRestClient;
    private final CircuitBreaker circuitBreaker;
    private final boolean resilienceEnabled;

    public PaymentClient(RestClient paymentRestClient, CircuitBreaker paymentCircuitBreaker,
            @Value("${payment.resilience.enabled:true}") boolean resilienceEnabled) {
        this.paymentRestClient = paymentRestClient;
        this.circuitBreaker = paymentCircuitBreaker;
        this.resilienceEnabled = resilienceEnabled;
    }

    public PaymentResponse charge(PaymentRequest request) {
        if (!resilienceEnabled) {
            // Контрольний варіант «без resilience»: прямий блокуючий виклик без CB.
            return doCharge(request);
        }
        Supplier<PaymentResponse> decorated =
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> doCharge(request));
        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            // Кільце розімкнено — миттєва відмова, виклик у мережу не пішов.
            throw new PaymentUnavailableException("payment circuit OPEN — fail fast", e);
        } catch (RuntimeException e) {
            // Виклик упав або перевищив таймаут 500мс.
            throw new PaymentUnavailableException("payment call failed: " + e.getMessage(), e);
        }
    }

    private PaymentResponse doCharge(PaymentRequest request) {
        return paymentRestClient.post()
                .uri("/payments")
                .body(request)
                .retrieve()
                .body(PaymentResponse.class);
    }
}
