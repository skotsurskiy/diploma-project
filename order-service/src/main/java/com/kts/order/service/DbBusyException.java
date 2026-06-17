package com.kts.order.service;

/**
 * Сигналізує, що семафор Bulkhead доступу до БД переповнений: одночасних звернень
 * до бази вже максимум, тож новий запит відхиляється миттєво (fail-fast), не
 * чекаючи вільного з'єднання й не вичерпуючи пул. Обробляється як HTTP 503.
 */
public class DbBusyException extends RuntimeException {

    public DbBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}
