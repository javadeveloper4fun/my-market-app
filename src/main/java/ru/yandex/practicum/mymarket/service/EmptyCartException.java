package ru.yandex.practicum.mymarket.service;

/**
 * Корзина пуста, оформить заказ не из чего.
 *
 * <p>Выделена в отдельный тип, чтобы {@code OrderController} отличал это ожидаемое
 * состояние от любой другой {@code IllegalStateException}. Если бы он ловил общий тип,
 * то любая непредвиденная ошибка внутри {@code buy()} тоже привела бы к редиректу
 * в корзину и была бы не видна в логах.
 */
public class EmptyCartException extends RuntimeException {

    /**
     * @param message описание причины
     */
    public EmptyCartException(String message) {
        super(message);
    }
}
