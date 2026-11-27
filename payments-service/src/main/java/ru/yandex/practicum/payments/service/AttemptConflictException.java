package ru.yandex.practicum.payments.service;

/**
 * Конфликт попытки оплаты: тот же attemptId с другим пользователем или суммой.
 * Возвращает контрактный HTTP 409 с кодом {@code ATTEMPT_CONFLICT}.
 */
public class AttemptConflictException extends RuntimeException {

    public AttemptConflictException(String message) {
        super(message);
    }
}
