package ru.yandex.practicum.mymarket.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Позиция корзины: товар, его количество и владелец (пользователь).
 *
 * <p>Связь с товаром хранится идентификатором {@code itemId}, а не ссылкой на
 * {@link Item}. Владелец позиции — {@code userId} — нужен для персонификации
 * корзины: каждый пользователь видит только свои позиции.
 */
@Table("cart_item")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class CartItem {

    @Id
    private Long id;

    private Long userId;

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

    /**
     * Создаёт позицию корзины для пользователя и товара.
     *
     * @param itemId идентификатор товара
     * @param count  количество товара в корзине
     * @param userId идентификатор пользователя — владельца позиции
     */
    public CartItem(Long itemId, int count, Long userId) {
        this.itemId = itemId;
        this.count = count;
        this.userId = userId;
    }
}
