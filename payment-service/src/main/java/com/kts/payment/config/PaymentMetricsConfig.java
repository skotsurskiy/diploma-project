package com.kts.payment.config;

import com.kts.payment.service.PaymentService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * Реєструє Micrometer-гауж поточної затримки payment
 * ({@code payment_processing_delay_ms}). На дашборді це лінія «ін'єкції відмови»:
 * коли затримка стрибає 200 → 2000 мс, видно момент початку деградації.
 */
@Configuration
public class PaymentMetricsConfig {

    public PaymentMetricsConfig(PaymentService paymentService, MeterRegistry meterRegistry) {
        Gauge.builder("payment.processing.delay.ms", paymentService, PaymentService::getProcessingDelayMs)
                .description("Поточна імітована затримка payment (мс)")
                .register(meterRegistry);
    }
}
