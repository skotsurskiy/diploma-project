package com.kts.payment.service;

import com.kts.payment.domain.Payment;
import com.kts.payment.domain.PaymentStatus;
import com.kts.payment.repository.PaymentRepository;
import com.kts.payment.web.dto.PaymentRequest;
import com.kts.payment.web.dto.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentRepository repository;

    /** Імітація часу взаємодії з зовнішнім платіжним провайдером (мс). */
    @Value("${payment.processing-delay-ms:200}")
    private long processingDelayMs;

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    public PaymentResponse process(PaymentRequest request) {
        // Імітація виклику зовнішнього платіжного шлюзу. Свідомо ПОЗА транзакцією
        // БД: тримати з'єднання HikariCP протягом затримки провайдера — антипатерн
        // (інакше payment упирається в пул=20 → стеля 100 RPS незалежно від
        // кількості потоків). Без цього binding-обмеженням стенду стають саме
        // платформні потоки Tomcat.
        simulateProviderLatency();

        // save() виконується у власній короткій транзакції (Spring Data) —
        // з'єднання утримується лише на час самого INSERT.
        Payment payment = new Payment(request.orderId(), request.amount(), PaymentStatus.APPROVED);
        Payment saved = repository.save(payment);

        return new PaymentResponse(saved.getId(), saved.getOrderId(), saved.getStatus());
    }

    private void simulateProviderLatency() {
        try {
            Thread.sleep(processingDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment processing interrupted", e);
        }
    }
}
