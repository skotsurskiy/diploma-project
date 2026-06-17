package com.kts.order.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP-клієнт до PaymentService.
 * <p>
 * Фаза 3.2.2: на відміну від baseline, додано ТАЙМАУТИ. Читання обмежене 500мс,
 * тож повільна відповідь payment перетворюється на помилку за 500мс (а не висить
 * 2с), яку фіксує Circuit Breaker. Це база для fail-fast.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient paymentRestClient(
            @Value("${payment.service.url:http://localhost:8081}") String baseUrl,
            @Value("${payment.resilience.enabled:true}") boolean resilienceEnabled) {
        // Перемикач для порівняння «з CB / без CB» на одному образі:
        // увімкнено  → таймаут 500мс (повільний payment стає помилкою);
        // вимкнено   → фактично без таймауту (65с) — оригінальна поведінка, виклик висить.
        Duration readTimeout = resilienceEnabled ? Duration.ofMillis(500) : Duration.ofSeconds(65);

        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofMillis(500))
                .withReadTimeout(readTimeout);
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
