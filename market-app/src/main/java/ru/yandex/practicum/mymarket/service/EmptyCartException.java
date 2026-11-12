package ru.yandex.practicum.mymarket.service;

/**
 * Корзина пуста, оформить заказ не из чего.
 *
 * <p>Выделена в отдельный тип, чтобы {@code OrderController} отличал это ожидаемое
 * состояние от любой другой {@code RuntimeException}. {@code EmptyCartException}
 * отделён от {@code IllegalStateException}, поэтому настоящая ошибка не растворяется
 * в редиректе: {@code OrderController.buy()} ловит только {@code EmptyCartException},
 * а любая непредвиденная ошибка обрабатывается {@code GlobalExceptionHandler}.
 *
 * <p>В отличие от ошибок, доходящих до {@code GlobalExceptionHandler}, это
 * исключение в лог не попадает: его перехватывает {@code onErrorResume} в
 * контроллере, и до обработчика оно не доходит. Логировать его незачем — пустая
 * корзина это штатное состояние, а не сбой, и пользователь уводится на страницу
 * корзины, где и видит причину.
 */
public class EmptyCartException extends RuntimeException {

    /**
     * @param message описание причины
     */
    public EmptyCartException(String message) {
        super(message);
    }
}
