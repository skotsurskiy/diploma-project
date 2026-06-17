package com.kts.order.client;

/**
 * Сигналізує, що платіж недоступний: або Circuit Breaker розімкнено (миттєва
 * відмова), або виклик payment не вдався/перевищив таймаут. Обробляється
 * контролером як HTTP 503 (швидка «розумна» відмова замість каскадного зависання).
 */
public class PaymentUnavailableException extends RuntimeException {

    public PaymentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
