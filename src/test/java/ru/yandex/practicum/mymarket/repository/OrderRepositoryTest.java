package ru.yandex.practicum.mymarket.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;

/**
 * Тесты репозитория заказов на встроенной H2 (профиль test).
 */
@DataJpaTest
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Проверяет сохранение заказа вместе со строками (каскад).
     */
    @Test
    void savesOrderWithItems() {
        Order order = new Order();
        order.addItem(new OrderItem(1L, "Куртка", new BigDecimal("4500.00"), 2));
        order.addItem(new OrderItem(2L, "Плед", new BigDecimal("1600.00"), 1));

        Order saved = orderRepository.saveAndFlush(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getItems()).extracting(OrderItem::getItemId).containsExactly(1L, 2L);
    }

    /**
     * Проверяет загрузку заказа со строками по fetch join.
     */
    @Test
    void findsOrderWithItemsByFetchJoin() {
        Order order = new Order();
        order.addItem(new OrderItem(1L, "Куртка", new BigDecimal("4500.00"), 2));
        Order saved = orderRepository.saveAndFlush(order);

        Order loaded = orderRepository.findWithItemsById(saved.getId()).orElseThrow();

        assertThat(loaded.getItems()).hasSize(1);
        assertThat(loaded.getItems().getFirst().getTitle()).isEqualTo("Куртка");
        assertThat(loaded.getItems().getFirst().getCount()).isEqualTo(2);
    }

    /**
     * Проверяет загрузку всех заказов сразу со строками.
     */
    @Test
    void findAllWithItemsLoadsOrders() {
        orderRepository.saveAndFlush(orderWith("Куртка"));
        orderRepository.saveAndFlush(orderWith("Плед"));

        List<Order> orders = orderRepository.findAllWithItems();

        assertThat(orders).hasSize(2);
        assertThat(orders).allSatisfy(o -> assertThat(o.getItems()).isNotEmpty());
    }

    /**
     * Создаёт заказ с одной строкой.
     *
     * @param title название товара в строке заказа
     * @return новый заказ
     */
    private Order orderWith(String title) {
        Order order = new Order();
        order.addItem(new OrderItem(1L, title, new BigDecimal("2500.00"), 1));
        return order;
    }
}
