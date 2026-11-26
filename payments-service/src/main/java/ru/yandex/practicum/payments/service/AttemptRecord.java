package ru.yandex.practicum.payments.service;

/**
 * Результат обработки одной идемпотентной попытки оплаты.
 * Хранится в {@link PaymentAttemptService} под ключом attemptId.
 *
 * @param userId          идентификатор пользователя витрины
 * @param amount          запрошенная сумма списания
 * @param status          {@link Status#PAID} или {@link Status#INSUFFICIENT_FUNDS}
 * @param remainingBalance остаток баланса после попытки (или текущий баланс при отказе)
 */
public record AttemptRecord(long userId, long amount, Status status, long remainingBalance) {

    public enum Status {
        PAID,
        INSUFFICIENT_FUNDS
    }
}
