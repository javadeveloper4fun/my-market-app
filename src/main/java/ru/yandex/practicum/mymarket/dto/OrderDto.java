package ru.yandex.practicum.mymarket.dto;

import java.math.BigDecimal;
import java.util.List;
import ru.yandex.practicum.mymarket.model.OrderItem;

/**
 * Данные заказа, возвращаемые контроллером.
 *
 * <p>Позиции приходят сущностями {@link OrderItem}: проекция для их чтения в
 * R2DBC ненадёжна — см. {@link ru.yandex.practicum.mymarket.repository.OrderItemRepository}.
 * Отдельной сборки DTO из сущности заказа нет: у неё больше нет коллекции
 * позиций, потому что R2DBC не сохраняет вложенные сущности.
 *
 * <p>Цена позиции и сумма заказа проходят усечение до целых рублей через
 * {@code BigDecimal.longValue()}: {@code 999.99 → 999}. Это осознанное
 * упрощение, зафиксированное тестами {@code getItemTruncatesFractionalPriceToWholeRubles}
 * и {@code keepsCentsInSnapshotButTruncatesThemInDto}.
 *
 * @param id       идентификатор заказа
 * @param items    позиции заказа
 * @param totalSum суммарная стоимость заказа
 */
public record OrderDto(long id, List<ItemDto> items, long totalSum) {

    /**
     * Собирает DTO заказа из его позиций.
     *
     * @param id    идентификатор заказа
     * @param items позиции заказа
     * @return DTO заказа
     */
    public static OrderDto of(long id, List<OrderItem> items) {
        List<ItemDto> dtos = items.stream()
                .map(row ->
                        new ItemDto(row.getItemId(), row.getTitle(), "", "", priceOf(row.getPrice()), row.getCount()))
                .toList();
        long totalSum = items.stream()
                .mapToLong(row -> priceOf(row.getPrice()) * row.getCount())
                .sum();
        return new OrderDto(id, dtos, totalSum);
    }

    /**
     * Приводит цену к целым рублям усечением.
     *
     * @param price цена из базы
     * @return цена в целых рублях
     */
    private static long priceOf(BigDecimal price) {
        return price.longValue();
    }
}
