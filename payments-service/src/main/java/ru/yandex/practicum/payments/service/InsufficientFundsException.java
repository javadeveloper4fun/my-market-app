package ru.yandex.practicum.payments.service;

/**
 * Запрошено списание на сумму, которой нет на балансе.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(long amount, long balance) {
        super("Недостаточно средств для списания " + amount + " руб., баланс: " + balance + " руб.");
    }
}
