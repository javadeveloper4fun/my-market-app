package ru.yandex.practicum.mymarket.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Строка заказа: снимок товара на момент покупки (название, цена, количество).
 */
@Entity
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Заказ, которому принадлежит строка. Это «владелец» связи ({@code @ManyToOne}),
     * поэтому колонка {@code order_id} создаётся здесь. Обратная сторона —
     * {@code Order.items} с {@code mappedBy = "order"}.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * Идентификатор купленного товара (снимок на момент покупки).
     * Хранится как обычное поле, а не FK на Item — чтобы заказ
     * не зависел от текущего состояния каталога.
     */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int count;

    /**
     * Создаёт строку заказа с данными товара на момент покупки.
     *
     * @param itemId идентификатор купленного товара
     * @param title название товара
     * @param price цена товара
     * @param count количество
     */
    public OrderItem(Long itemId, String title, BigDecimal price, int count) {
        this.itemId = itemId;
        this.title = title;
        this.price = price;
        this.count = count;
    }
}
