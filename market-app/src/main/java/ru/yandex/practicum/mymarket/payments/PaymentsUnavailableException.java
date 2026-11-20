package ru.yandex.practicum.mymarket.payments;

/**
 * Сервис платежей недоступен или ответил ошибкой.
 */
public class PaymentsUnavailableException extends RuntimeException {

    public PaymentsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentsUnavailableException(String message) {
        super(message);
    }
}
