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
 * <p>Страницы заказов собираются двумя запросами: идентификаторы заказов и
 * позиции по ним. Раньше для этого служил {@code left join fetch} с
 * {@code distinct}, где {@code distinct} маскировал размножение строки заказа по
 * числу позиций.
 *
 * <p>{@code @Transactional} работает за счёт бина {@code R2dbcTransactionManager}:
 * транзакция открывается при подписке на {@code Mono} и закрывается по его
 * завершении.
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
     * Возвращает все заказы.
     *
     * @return список заказов с позициями
     */
    public Mono<List<OrderDto>> getOrders() {
        return orderRepository.findAllIds().collectList().flatMap(ids -> {
            if (ids.isEmpty()) {
                return Mono.just(List.of());
            }
            return orderItemRepository.findAllByOrderIdIn(ids).collectList().map(items -> toOrderDtos(ids, items));
        });
    }

    /**
     * Возвращает один заказ.
     *
     * @param id идентификатор заказа
     * @return заказ с позициями
     */
    public Mono<OrderDto> getOrder(long id) {
        return orderRepository
                .findById(id)
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(id)))
                .flatMap(order -> orderItemRepository
                        .findAllByOrderIdIn(List.of(order.getId()))
                        .collectList()
                        .map(items -> OrderDto.of(order.getId(), items)));
    }

    /**
     * Оформляет заказ из корзины и очищает её.
     *
     * <p>Создание заказа, его позиций и очистка корзины должны выполниться одной
     * транзакцией: иначе возможна покупка без заказа или товар в корзине после
     * покупки.
     *
     * <p>Заказ и позиции сохраняются отдельными вызовами, и это не стилистический
     * выбор. R2DBC не умеет писать вложенные сущности: попытка сохранить заказ
     * вместе с коллекцией позиций заканчивается
     * {@code InvalidDataAccessApiUsageException: Nested entities are not supported}.
     * Приём {@code @MappedCollection}, сохраняющий агрегат целиком, работает в
     * Spring Data JDBC, но не здесь. Поэтому сначала вставляется строка заказа,
     * затем позиции с уже известным {@code order_id}.
     *
     * <p>Позиции сохраняются последовательно через {@code concatMap}, а не
     * параллельно: так порядок вставок совпадает с порядком корзины.
     *
     * @return идентификатор созданного заказа
     */
    @Transactional
    public Mono<Long> buy() {
        return cartItemRepository
                .findAll()
                .collectList()
                .flatMap(cartItems -> {
                    if (cartItems.isEmpty()) {
                        return Mono.error(new EmptyCartException("Cart is empty"));
                    }
                    return snapshot(cartItems).flatMap(this::saveOrderWithItems);
                })
                .flatMap(orderId -> cartItemRepository.deleteAll().thenReturn(orderId));
    }

    /**
     * Сохраняет заказ и его позиции.
     *
     * @param items позиции заказа без идентификатора заказа
     * @return идентификатор созданного заказа
     */
    private Mono<Long> saveOrderWithItems(List<OrderItem> items) {
        return orderRepository
                .save(new Order())
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
     * <p>Позиция корзины, товар которой не найден, приводит к ошибке, а не
     * пропускается молча. Именно молчаливый пропуск был бы худшим исходом:
     * заказ оформился бы на сумму меньше, чем показывает корзина, и покупатель
     * обнаружил бы это уже на странице заказа, когда позицию нельзя ни вернуть,
     * ни предъявить.
     *
     * <p>На практике случай недостижим: на {@code cart_item.item_id} стоит
     * внешний ключ на каталог, и удалить товар, пока он лежит в корзине, база не
     * даст. Проверка оставлена намеренно: она ничего не стоит в нормальном
     * сценарии и превращает невозможное расхождение в явную ошибку вместо
     * заказа с недобором позиций.
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
