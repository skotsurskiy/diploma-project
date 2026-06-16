package com.kts.payment.service;

import com.kts.payment.domain.Payment;
import com.kts.payment.domain.PaymentStatus;
import com.kts.payment.repository.PaymentRepository;
import com.kts.payment.web.dto.PaymentRequest;
import com.kts.payment.web.dto.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository repository;

    /** Імітація часу взаємодії з зовнішнім платіжним провайдером (мс). */
    @Value("${payment.processing-delay-ms:200}")
    private long processingDelayMs;

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PaymentResponse process(PaymentRequest request) {
        simulateProviderLatency();

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
