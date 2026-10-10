package ru.yandex.practicum.mymarket.service;

/**
 * Запрошенный заказ не найден.
 *
 * <p>Аналогично {@link ItemNotFoundException}: «не найдено» — это 404, а не 400, и смешивать
 * его с некорректным аргументом не следует.
 */
public class OrderNotFoundException extends RuntimeException {

    /**
     * @param orderId идентификатор ненайденного заказа
     */
    public OrderNotFoundException(long orderId) {
        super("Order not found: " + orderId);
    }
}
