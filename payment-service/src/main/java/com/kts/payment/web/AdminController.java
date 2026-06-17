package com.kts.payment.web;

import com.kts.payment.service.PaymentService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Адмін-ендпоінт для ін'єкції відмови в рантаймі (chaos): дозволяє миттєво
 * змінити затримку payment без рестарту контейнера. Потрібен для побудови
 * чистої часової шкали «здоровий → деградація → відновлення».
 *
 * Приклад: curl -X POST "http://localhost:8081/admin/delay?ms=2000"
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final PaymentService paymentService;

    public AdminController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/delay")
    public Map<String, Long> setDelay(@RequestParam long ms) {
        paymentService.setProcessingDelayMs(ms);
        return Map.of("processingDelayMs", paymentService.getProcessingDelayMs());
    }
}
