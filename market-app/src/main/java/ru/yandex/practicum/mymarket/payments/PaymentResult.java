package ru.yandex.practicum.mymarket.payments;

/**
 * Результат успешного списания.
 *
 * @param amount  списанная сумма
 * @param balance остаток баланса
 */
public record PaymentResult(long amount, long balance) {}
