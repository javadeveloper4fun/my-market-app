package ru.yandex.practicum.payments.service;

/**
 * Сумма платежа отсутствует или неположительна.
 */
public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException() {
        super("Сумма платежа должна быть положительной");
    }
}
