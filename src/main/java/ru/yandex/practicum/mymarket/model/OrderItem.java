package ru.yandex.practicum.mymarket.model;

import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Строка заказа: товар, его количество и цена на момент покупки.
 *
 * <p>Идентификатор купленного товара хранится обычным полем {@code itemId}, а
 * не ссылкой на {@link Item}: в таблице {@code order_item} нет внешнего ключа на
 * каталог, и заказ не зависит от текущего состояния товаров.
 *
 * <p>{@code orderId} — обычная колонка {@code order_id} с внешним ключом на
 * {@code orders}. Раньше связь выражалась через {@code @MappedCollection} на
 * {@link Order}, но R2DBC не умеет сохранять вложенные сущности, поэтому поле
 * пришлось вернуть и заполнять вручную. Поле {@code id} остаётся пустым при
 * вставке — его подставит база.
 */
@Table("order_item")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class OrderItem {

    @Id
    private Long id;

    private Long orderId;

    private Long itemId;

    private String title;

    private BigDecimal price;

    private int count;

    /**
     * Создаёт позицию заказа для товара.
     *
     * @param orderId идентификатор заказа, к которому относится позиция
     * @param itemId  идентификатор купленного товара
     * @param title   название товара
     * @param price   цена товара
     * @param count   количество товара
     */
    public OrderItem(Long orderId, Long itemId, String title, BigDecimal price, int count) {
        this.orderId = orderId;
        this.itemId = itemId;
        this.title = title;
        this.price = price;
        this.count = count;
    }
}
