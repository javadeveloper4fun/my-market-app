package ru.yandex.practicum.mymarket.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Позиция корзины: товар и его количество.
 *
 * <p>Связь с товаром хранится идентификатором {@code itemId}, а не ссылкой на
 * {@link Item}. В R2DBC нет ленивой загрузки и объектного графа: вложенная
 * ссылка маппилась бы в колонку по {@code @Id} вложенного объекта, но при
 * записи Spring Data попытался бы сохранить и его тоже. Плоский
 * {@code itemId} делает запись предсказуемой.
 *
 * <p>Данные для страницы корзины добираются вторым запросом по идентификаторам,
 * а не выборкой с join — почему так, объяснено в
 * {@link ru.yandex.practicum.mymarket.repository.CartItemRepository}.
 */
@Table("cart_item")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class CartItem {

    @Id
    private Long id;

    private Long itemId;

    private int count;

    /**
     * Создаёт позицию корзины для товара.
     *
     * @param itemId идентификатор товара
     * @param count  количество товара в корзине
     */
    public CartItem(Long itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }
}
