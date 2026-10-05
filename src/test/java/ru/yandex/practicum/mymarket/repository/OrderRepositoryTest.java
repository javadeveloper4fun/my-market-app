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
     * Проверяет, что заказ с несколькими позициями загружается целиком и одним объектом.
     *
     * <p>{@code findWithItemsById} соединяет таблицы через {@code left join fetch}, и на
     * уровне SQL одна строка заказа даёт столько строк, сколько у него позиций.
     * Проверка нужна именно для нескольких позиций: заказ с одной позицией этой особенности
     * запроса не касается, поэтому такой заказ не может выявить регрессию.
     *
     * <p>Отдельно проверено, что {@code distinct} в запросе не единственное, что удерживает
     * результат от дублей: Hibernate 7 сам устраняет повторы корневой сущности, поэтому снятие
     * {@code distinct} тест на этой версии не роняет. В JPQL, в отличие от SQL, {@code distinct}
     * не влияет на число возвращаемых строк и остаётся осмысленным только как фиксация
     * ожидаемого поведения запроса.
     */
    @Test
    void findsOrderWithTwoItemsWithoutDuplicatingOrder() {
        Order order = new Order();
        order.addItem(new OrderItem(1L, "Куртка", new BigDecimal("4500.00"), 2));
        order.addItem(new OrderItem(2L, "Плед", new BigDecimal("1600.00"), 1));
        Order saved = orderRepository.saveAndFlush(order);

        Order loaded = orderRepository.findWithItemsById(saved.getId()).orElseThrow();

        assertThat(loaded.getItems()).hasSize(2);
        assertThat(loaded.getItems()).extracting(OrderItem::getTitle).containsExactlyInAnyOrder("Куртка", "Плед");
    }

    /**
     * Проверяет загрузку всех заказов сразу со строками.
     *
     * <p>Заказы берутся с несколькими позициями: при одной позиции на заказ соединение не
     * размножает строки, поэтому проверка на дубли ничего не значила бы.
     */
    @Test
    void findAllWithItemsLoadsOrders() {
        orderRepository.saveAndFlush(multiItemOrder());
        orderRepository.saveAndFlush(multiItemOrder());

        List<Order> orders = orderRepository.findAllWithItems();

        assertThat(orders).hasSize(2);
        assertThat(orders).allSatisfy(o -> assertThat(o.getItems()).hasSize(2));
    }

    /**
     * Создаёт заказ с двумя строками.
     *
     * <p>Нужен для проверки поведения fetch join на заказах с несколькими позициями:
     * при одном товаре в заказе соединение не размножает строки, и защищать нечего.
     *
     * @return новый заказ с двумя позициями
     */
    private Order multiItemOrder() {
        Order order = new Order();
        order.addItem(new OrderItem(1L, "Куртка", new BigDecimal("4500.00"), 2));
        order.addItem(new OrderItem(2L, "Плед", new BigDecimal("1600.00"), 1));
        return order;
    }
}
