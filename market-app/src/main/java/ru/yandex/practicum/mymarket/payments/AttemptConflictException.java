package ru.yandex.practicum.mymarket.payments;

/**
 * Конфликт попытки оплаты: тот же attemptId уже используется с другим
 * пользователем или суммой. Возвращает HTTP 409.
 */
public class AttemptConflictException extends RuntimeException {

    public AttemptConflictException(String message) {
        super(message);
    }
}
