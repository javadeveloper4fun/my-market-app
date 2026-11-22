package ru.yandex.practicum.mymarket.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Запрошенный заказ не найден.
 *
 * <p>Аналогично {@link ItemNotFoundException}: «не найдено» — это 404, а не 400, и смешивать
 * его с некорректным аргументом не следует. Статус задан так же — на исключении,
 * а не в обработчике, чтобы ответ отрисовался страницей ошибки.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OrderNotFoundException extends RuntimeException {

    /**
     * @param orderId идентификатор ненайденного заказа
     */
    public OrderNotFoundException(long orderId) {
        super("Order not found: " + orderId);
    }
}
