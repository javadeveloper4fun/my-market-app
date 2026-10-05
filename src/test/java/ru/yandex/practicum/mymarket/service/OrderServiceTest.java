package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    void getOrdersReturnsAllWithItemsAndTotalSum() {
        Order order = new Order();
        var items = List.of(
                new OrderItem(1L, "Тестовая куртка", new BigDecimal("1000"), 2),
                new OrderItem(2L, "Тестовый рюкзак", new BigDecimal("2000"), 1));
        items.forEach(oi -> oi.setOrder(order));
        order.setItems(items);
        orderRepository.save(order);

        List<OrderDto> orders = orderService.getOrders();

        assertThat(orders).hasSize(1);
        OrderDto dto = orders.get(0);
        assertThat(dto.items()).hasSize(2);
        assertThat(dto.items()).extracting(ItemDto::id).containsExactly(1L, 2L);
        assertThat(dto.totalSum()).isEqualTo(1000L * 2 + 2000L * 1);
    }

    @Test
    void getOrderReturnsCorrectSnapshot() {
        Item item = itemRepository.save(
                new Item("Тестовый товар", "Описание", "img.jpg", new java.math.BigDecimal("1500")));

        Order order = new Order();
        var items = List.of(new OrderItem(item.getId(), "Тестовый товар", new BigDecimal("1500"), 3));
        items.forEach(oi -> oi.setOrder(order));
        order.setItems(items);
        Order saved = orderRepository.save(order);

        OrderDto dto = orderService.getOrder(saved.getId());

        assertThat(dto.id()).isEqualTo(saved.getId());
        assertThat(dto.items()).hasSize(1);
        ItemDto itemDto = dto.items().get(0);
        assertThat(itemDto.id()).isEqualTo(item.getId());
        assertThat(itemDto.title()).isEqualTo("Тестовый товар");
        assertThat(itemDto.count()).isEqualTo(3);
        assertThat(itemDto.price()).isEqualTo(1500L);
        assertThat(dto.totalSum()).isEqualTo(1500L * 3);
    }

    @Test
    void buyCreatesOrderFromCartAndClearsCart() {
        Item item1 = itemRepository.save(
                new Item("Тестовая куртка", "Зимняя", "img1.jpg", new java.math.BigDecimal("1000")));
        Item item2 = itemRepository.save(
                new Item("Тестовый рюкзак", "Городской", "img2.jpg", new java.math.BigDecimal("2000")));
        cartItemRepository.save(new CartItem(item1, 2));
        cartItemRepository.save(new CartItem(item2, 1));

        long orderId = orderService.buy();

        assertThat(orderId).isPositive();
        Order saved = orderRepository.findWithItemsById(orderId).orElseThrow();
        assertThat(saved.getItems()).hasSize(2);
        assertThat(cartItemRepository.findAllWithItem()).isEmpty();
    }

    @Test
    void buyThrowsWhenCartEmpty() {
        assertThatThrownBy(() -> orderService.buy())
                .isInstanceOf(EmptyCartException.class)
                .hasMessageContaining("Cart is empty");
    }

    @Test
    void buyPreservesSnapshotPrices() {
        Item item = itemRepository.save(
                new Item("Тестовый товар", "Описание", "img.jpg", new java.math.BigDecimal("1500")));
        cartItemRepository.save(new CartItem(item, 2));

        long orderId = orderService.buy();

        Order saved = orderRepository.findWithItemsById(orderId).orElseThrow();
        assertThat(saved.getItems().get(0).getPrice()).isEqualByComparingTo(new java.math.BigDecimal("1500"));
    }
}
