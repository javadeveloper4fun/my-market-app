package ru.yandex.practicum.mymarket.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Заказ покупателя. Содержит строки заказа (зафиксированные товары с ценой на момент покупки).
 */
@Entity
@Table(name = "orders")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Строки заказа. Связь — «обратная» сторона ({@code mappedBy}), поэтому колонка
     * {@code order_id} создаётся в {@code order_item} на стороне {@code OrderItem}.
     *
     * <p>{@code CascadeType.ALL} позволяет сохранить заказ со строками одним
     * {@code save()}, {@code orphanRemoval} удаляет строки вместе с заказом.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /**
     * Добавляет строку заказа и связывает её с этим заказом.
     *
     * @param orderItem строка заказа
     */
    public void addItem(OrderItem orderItem) {
        orderItem.setOrder(this);
        items.add(orderItem);
    }
}
