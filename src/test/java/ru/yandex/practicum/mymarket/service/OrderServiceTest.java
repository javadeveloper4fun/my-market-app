package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;

/**
 * Тесты сервиса заказов на встроенной H2 (профиль test).
 *
 * <p>Изоляция тестов — через явную очистку базы вместо {@code @Transactional};
 * почему отката на реактивном стеке не происходит, сказано в
 * {@link DatabaseResetTest}.
 *
 * <p>Вызовы завершаются на {@code block()}: методы сервиса возвращают
 * {@code Mono}, и без подписки не выполняется ни одного запроса.
 *
 * <p>Заказы и позиции создаются отдельными вызовами, а не сборкой агрегата: R2DBC
 * не сохраняет вложенные сущности.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderServiceTest extends DatabaseResetTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    void getOrdersReturnsAllWithItemsAndTotalSum() {
        saveOrderWithItems(
                new OrderItem(null, 1L, "Тестовая куртка", new BigDecimal("1000"), 2),
                new OrderItem(null, 2L, "Тестовый рюкзак", new BigDecimal("2000"), 1));

        List<OrderDto> orders = orderService.getOrders().block();

        assertThat(orders).hasSize(1);
        OrderDto dto = orders.get(0);
        assertThat(dto.items()).hasSize(2);
        assertThat(dto.items()).extracting(ItemDto::id).containsExactlyInAnyOrder(1L, 2L);
        assertThat(dto.totalSum()).isEqualTo(1000L * 2 + 2000L * 1);
    }

    @Test
    void getOrderReturnsCorrectSnapshot() {
        Item item = saveItem("Тестовый товар", "Описание", "1500");
        long orderId =
                saveOrderWithItems(new OrderItem(null, item.getId(), "Тестовый товар", new BigDecimal("1500"), 3));

        OrderDto dto = orderService.getOrder(orderId).block();

        assertThat(dto.id()).isEqualTo(orderId);
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
        Item item1 = saveItem("Тестовая куртка", "Зимняя", "1000");
        Item item2 = saveItem("Тестовый рюкзак", "Городской", "2000");
        cartItemRepository.save(new CartItem(item1.getId(), 2)).block();
        cartItemRepository.save(new CartItem(item2.getId(), 1)).block();

        long orderId = orderService.buy().block();

        assertThat(orderId).isPositive();
        List<OrderItem> savedItems = orderItemRepository
                .findAllByOrderIdIn(List.of(orderId))
                .collectList()
                .block();
        assertThat(savedItems).hasSize(2);
        assertThat(savedItems)
                .allSatisfy(saved -> assertThat(saved.getOrderId()).isEqualTo(orderId));
        assertThat(cartItemRepository.findAll().collectList().block()).isEmpty();
    }

    /**
     * Проверяет, что количество из корзины переносится в заказ.
     *
     * <p>Проверка не на вторую позицию ради количества: снимок позиций строится
     * до вставки заказа, и на этом шаге легко потерять счётчик, оставив
     * фиксированную единицу.
     */
    @Test
    void buyPreservesCountsFromCart() {
        Item jacket = saveItem("Тестовая куртка", "Зимняя", "1000");
        Item boots = saveItem("Тестовые ботинки", "Кожаные", "2000");
        cartItemRepository.save(new CartItem(jacket.getId(), 3)).block();
        cartItemRepository.save(new CartItem(boots.getId(), 1)).block();

        long orderId = orderService.buy().block();

        List<OrderItem> savedItems = orderItemRepository
                .findAllByOrderIdIn(List.of(orderId))
                .collectList()
                .block();
        assertThat(savedItems.stream().filter(i -> i.getItemId().equals(jacket.getId())))
                .singleElement()
                .extracting(OrderItem::getCount)
                .isEqualTo(3);
    }

    @Test
    void buyThrowsWhenCartEmpty() {
        assertThatThrownBy(() -> orderService.buy().block())
                .isInstanceOf(EmptyCartException.class)
                .hasMessageContaining("Cart is empty");
    }

    @Test
    void buyPreservesSnapshotPrices() {
        Item item = saveItem("Тестовый товар", "Описание", "1500");
        cartItemRepository.save(new CartItem(item.getId(), 2)).block();

        long orderId = orderService.buy().block();

        OrderItem saved =
                orderItemRepository.findAllByOrderIdIn(List.of(orderId)).blockFirst();
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("1500"));
    }

    /**
     * Фиксирует границу округления цены при оформлении заказа.
     *
     * <p>Снимок цены в заказе хранится с точностью до копеек, а {@code OrderDto} отдаёт
     * её как {@code long} и отбрасывает дробную часть. Тест проверяет обе стороны:
     * в базе остаются все копейки, а витрина заказа показывает целые рубли. Если поменять
     * представление цены на {@code BigDecimal}, упадёт именно этот тест — то есть решение
     * о целых рублях придётся принимать осознанно, а не случайно.
     */
    @Test
    void keepsCentsInSnapshotButTruncatesThemInDto() {
        Item item = saveItem("Тестовый товар", "Описание", "999.99");
        cartItemRepository.save(new CartItem(item.getId(), 2)).block();

        long orderId = orderService.buy().block();

        OrderItem saved =
                orderItemRepository.findAllByOrderIdIn(List.of(orderId)).blockFirst();
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("999.99"));

        OrderDto dto = orderService.getOrder(orderId).block();
        assertThat(dto.totalSum()).isEqualTo(999L * 2);
        assertThat(dto.items().getFirst().price()).isEqualTo(999L);
    }

    @Test
    void getOrderFailsForUnknownId() {
        assertThatThrownBy(() -> orderService.getOrder(999999L).block()).isInstanceOf(OrderNotFoundException.class);
    }

    /**
     * Создаёт заказ и его позиции отдельными вызовами.
     *
     * @param items позиции заказа без идентификатора заказа
     * @return идентификатор созданного заказа
     */
    private long saveOrderWithItems(OrderItem... items) {
        Order order = orderRepository.save(new Order()).block();
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemRepository.save(item).block();
        }
        return order.getId();
    }

    /**
     * Создаёт и сохраняет товар.
     *
     * @param title       название
     * @param description описание
     * @param price       цена
     * @return сохранённый товар
     */
    private Item saveItem(String title, String description, String price) {
        return itemRepository
                .save(new Item(title, description, "img.jpg", new BigDecimal(price)))
                .block();
    }
}
