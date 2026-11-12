package ru.yandex.practicum.mymarket.payments;

/**
 * На балансе недостаточно средств для оплаты заказа.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException() {
        super("Недостаточно средств для оплаты заказа");
    }
}
