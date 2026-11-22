package ru.yandex.practicum.mymarket.payments;

public class InvalidPaymentRequestException extends RuntimeException {
    public InvalidPaymentRequestException(String message) {
        super(message);
    }

    public InvalidPaymentRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
