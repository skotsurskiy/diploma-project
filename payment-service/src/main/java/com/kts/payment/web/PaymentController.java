package com.kts.payment.web;

import com.kts.payment.service.PaymentService;
import com.kts.payment.web.dto.PaymentRequest;
import com.kts.payment.web.dto.PaymentResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public PaymentResponse pay(@RequestBody PaymentRequest request) {
        return paymentService.process(request);
    }
}
