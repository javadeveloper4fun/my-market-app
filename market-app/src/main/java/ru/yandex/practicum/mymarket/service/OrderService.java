package ru.yandex.practicum.mymarket.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

/**
 * Заказы покупателя и их оформление.
 *
 * <p>Все операции выполняются в контексте конкретного пользователя ({@code userId}).
 * Страницы заказов собираются двумя запросами: идентификаторы заказов и
 * позиции по ним. Раньше для этого служил {@code left join fetch} с
 * {@code distinct}, где {@code distinct} маскировал размножение строки заказа по
 * числу позиций.
 *
 * <p>Тема 4/11 «Реактивный доступ к данным в Spring Data R2DBC», раздел
 * «Декларативное управление транзакциями через {@code @Transactional}»:
 * {@code @Transactional} работает за счёт бина {@code R2dbcTransactionManager} —
 * на реактивном стеке менеджер транзакций другой, блокирующий
 * {@code DataSourceTransactionManager} не подходит. Транзакция открывается при
 * подписке на {@code Mono} и закрывается по его завершении.
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final ItemRepository itemRepository;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CartItemRepository cartItemRepository,
            ItemRepository itemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * Возвращает все заказы пользователя.
     *
     * <p>Заказы сортируются по идентификатору убыванию: {@code id} автоинкрементный,
     * поэтому свежие заказы — первыми.
     *
     * @param userId идентификатор пользователя
     * @return список заказов с позициями
     */
    public Mono<List<OrderDto>> getOrders(Long userId) {
        return orderRepository.findAllIdsByUserId(userId).collectList().flatMap(ids -> {
            if (ids.isEmpty()) {
                return Mono.just(List.of());
            }
            return orderItemRepository.findAllByOrderIdIn(ids).collectList().map(items -> toOrderDtos(ids, items));
        });
    }

    /**
     * Возвращает один заказ пользователя.
     *
     * @param id     идентификатор заказа
     * @param userId идентификатор пользователя
     * @return заказ с позициями или ошибка, если заказа нет или он чужой
     */
    public Mono<OrderDto> getOrder(long id, Long userId) {
        return orderRepository
                .findByIdAndUserId(id, userId)
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(id)))
                .flatMap(order -> orderItemRepository
                        .findAllByOrderIdIn(List.of(order.getId()))
                        .collectList()
                        .map(items -> OrderDto.of(order.getId(), items)));
    }

    /**
     * Готовит заказ из корзины пользователя, но не сохраняет его.
     *
     * <p>Между подготовкой и сохранением выполняется оплата. Если бы оплата
     * вызывалась внутри транзакции, внешний HTTP-запрос удерживал бы соединение
     * с базой всё время ожидания ответа платежного сервиса.
     *
     * @param userId идентификатор пользователя
     * @return снимок заказа с зафиксированными ценами и суммой
     */
    public Mono<PreparedOrder> prepareOrder(Long userId) {
        return cartItemRepository.findAllSortedByUserId(userId).collectList().flatMap(cartItems -> {
            if (cartItems.isEmpty()) {
                return Mono.error(new EmptyCartException("Cart is empty"));
            }
            return snapshot(cartItems).map(PreparedOrder::of);
        });
    }

    /**
     * Сохраняет оплаченный заказ и уменьшает позиции корзины на купленное количество.
     *
     * <p>Создание заказа, его позиций и изменение корзины должны выполниться одной
     * транзакцией: иначе возможна покупка без заказа или рассинхронизация количества.
     *
     * @param prepared подготовленный заказ
     * @param userId   идентификатор пользователя — владельца заказа
     * @return идентификатор созданного заказа
     */
    @Transactional
    public Mono<Long> placeOrder(PreparedOrder prepared, Long userId) {
        // Из корзины вычитается только оплаченное количество: товар, добавленный
        // после подготовки заказа, должен остаться в корзине.
        return saveOrderWithItems(prepared.items(), userId)
                .flatMap(orderId -> Flux.fromIterable(prepared.items())
                        .concatMap(item -> cartItemRepository
                                .decreaseCount(item.getItemId(), item.getCount(), userId)
                                .then(cartItemRepository.deleteIfEmpty(item.getItemId(), userId)))
                        .then(Mono.just(orderId)));
    }

    /**
     * Сохраняет заказ и его позиции.
     *
     * @param items  позиции заказа без идентификатора заказа
     * @param userId идентификатор пользователя — владельца заказа
     * @return идентификатор созданного заказа
     */
    private Mono<Long> saveOrderWithItems(List<OrderItem> items, Long userId) {
        return orderRepository
                .save(new Order(userId))
                .flatMap(order -> Flux.fromIterable(items)
                        .concatMap(item -> {
                            item.setOrderId(order.getId());
                            return orderItemRepository.save(item);
                        })
                        .then(Mono.just(order.getId())));
    }

    /**
     * Собирает снимок позиций: товары уходят в заказ с теми же названием, ценой
     * и количеством, а не ссылкой на каталог. Идентификатор заказа пока пуст —
     * его проставит {@link #saveOrderWithItems} после вставки заказа.
     *
     * @param cartItems позиции корзины
     * @return позиции заказа в том же порядке
     */
    private Mono<List<OrderItem>> snapshot(List<CartItem> cartItems) {
        List<Long> itemIds = cartItems.stream().map(CartItem::getItemId).toList();
        return itemRepository.findAllById(itemIds).collectList().map(items -> {
            Map<Long, Item> byId = items.stream().collect(Collectors.toMap(Item::getId, Function.identity()));
            List<OrderItem> orderItems = new ArrayList<>(cartItems.size());
            for (CartItem cartItem : cartItems) {
                Item item = byId.get(cartItem.getItemId());
                if (item == null) {
                    throw new ItemNotFoundException(cartItem.getItemId());
                }
                orderItems.add(
                        new OrderItem(null, item.getId(), item.getTitle(), item.getPrice(), cartItem.getCount()));
            }
            return orderItems;
        });
    }

    /**
     * Раскладывает позиции по заказам и собирает список DTO в порядке заказов.
     *
     * @param ids   идентификаторы заказов в порядке выдачи
     * @param items позиции заказов
     * @return список заказов
     */
    private List<OrderDto> toOrderDtos(List<Long> ids, List<OrderItem> items) {
        Map<Long, List<OrderItem>> byOrder = items.stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        return ids.stream()
                .map(id -> OrderDto.of(id, byOrder.getOrDefault(id, List.of())))
                .toList();
    }
}
