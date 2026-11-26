package ru.yandex.practicum.payments.service;

/**
 * Запрошено списание на сумму, которой нет на балансе.
 */
public class InsufficientFundsException extends RuntimeException {

    private final long amount;
    private final long balance;

    public InsufficientFundsException(long amount, long balance) {
        super("Недостаточно средств для списания " + amount + " руб., баланс: " + balance + " руб.");
        this.amount = amount;
        this.balance = balance;
    }

    public long getAmount() {
        return amount;
    }

    public long getCurrentBalance() {
        return balance;
    }
}
