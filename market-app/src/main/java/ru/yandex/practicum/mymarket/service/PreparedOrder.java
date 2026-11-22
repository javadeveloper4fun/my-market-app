package ru.yandex.practicum.mymarket.service;

import java.util.List;
import ru.yandex.practicum.mymarket.model.OrderItem;

/**
 * Подготовленный, но ещё не сохранённый заказ.
 *
 * <p>Снимок корзины отделён от записи в базу, чтобы между ними можно было
 * вызвать сервис платежей. Вызов оплаты не должен выполняться внутри
 * транзакции R2DBC: сетевой запрос удерживал бы соединение с базой и
 * транзакцию открытой.
 *
 * @param items позиции заказа с зафиксированными ценами
 * @param total сумма к оплате в целых рублях
 */
public record PreparedOrder(List<OrderItem> items, long total) {

    public PreparedOrder {
        items = List.copyOf(items);
    }

    /**
     * Собирает заказ из позиций и считает сумму.
     *
     * @param items позиции заказа
     * @return подготовленный заказ
     */
    public static PreparedOrder of(List<OrderItem> items) {
        long total = items.stream()
                .mapToLong(item -> item.getPrice().longValue() * item.getCount())
                .sum();
        return new PreparedOrder(items, total);
    }
}
