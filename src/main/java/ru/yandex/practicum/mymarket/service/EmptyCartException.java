package ru.yandex.practicum.mymarket.service;

/**
 * Корзина пуста, оформить заказ не из чего.
 *
 * <p>Выделена в отдельный тип, чтобы {@code OrderController} отличал это ожидаемое
 * состояние от любой другой {@code RuntimeException}. {@code EmptyCartException}
 * отделен от {@code IllegalStateException}, поэтому настоящая ошибка не растворяется
 * в редиректе: {@code OrderController.buy()} ловит только {@code EmptyCartException},
 * а любая непредвиденная ошибка обрабатывается {@code GlobalExceptionHandler}.
 * Поскольку исключение логируется в {@code GlobalExceptionHandler}, информация о
 * ней всегда сохраняется в логах.
 */
public class EmptyCartException extends RuntimeException {

    /**
     * @param message описание причины
     */
    public EmptyCartException(String message) {
        super(message);
    }
}
