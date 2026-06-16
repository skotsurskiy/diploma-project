package com.kts.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Baseline-конфігурація HTTP-клієнта (Фаза 3.1).
 * <p>
 * Навмисно БЕЗ таймаутів: запит до PaymentService блокує потік Tomcat
 * на весь час обробки платежу. Це і є джерело каскадної відмови,
 * яку ми діагностуємо в підрозділі 3.1 та усуваємо в 3.2.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient paymentRestClient(@Value("${payment.service.url:http://localhost:8081}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
