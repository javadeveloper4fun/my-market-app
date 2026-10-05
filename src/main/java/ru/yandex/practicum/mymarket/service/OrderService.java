package ru.yandex.practicum.mymarket.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.model.OrderItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

// Спринт 5, Тема 2, Урок 2 — транзакции.
// Уровень "высокий": @Transactional — декларативная аннотация + AOP-прокси.
// Spring открывает транзакцию, вызывает метод, фиксирует или откатывает.
// Под капотом используется TransactionManager (JpaTransactionManager).
@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;

    public OrderService(OrderRepository orderRepository, CartItemRepository cartItemRepository) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
    }

    /**
     * Возвращает все заказы.
     *
     * <p>Коллекция позиций ленивая, поэтому загружается в репозитории через
     * {@code left join fetch} — иначе обращение к {@code order.getItems()} даст
     * {@code LazyInitializationException}.
     *
     * @return список заказов
     */
    public List<OrderDto> getOrders() {
        return orderRepository.findAllWithItems().stream()
                .map(OrderDto::fromEntity)
                .toList();
    }

    public OrderDto getOrder(long id) {
        Order order = orderRepository.findWithItemsById(id).orElseThrow(() -> new OrderNotFoundException(id));
        return OrderDto.fromEntity(order);
    }

    /**
     * Оформление заказа (Спринт 5, Тема 2, Урок 2 — транзакции).
     * Создание заказа, его позиций и очистка корзины должны выполниться
     * одной транзакцией: иначе возможна покупка без заказа или товар в корзине после покупки.
     *
     * <p>Очистка корзины одним {@code deleteAllInBatch()} — батчевая операция:
     * один DELETE ... WHERE id IN (...) вместо удаления позиций по одной.
     *
     * @return идентификатор созданного заказа
     */
    // Спринт 5, Тема 2, Урок 4 — декларативная транзакция.
    // Вызов снаружи бина (из контроллера) проходит через AOP-прокси:
    // открытие транзакции → выполнение → commit/rollback по исключению.
    @Transactional
    public long buy() {
        List<CartItem> cartItems = cartItemRepository.findAllWithItem();
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        Order order = new Order();
        for (CartItem cartItem : cartItems) {
            Item item = cartItem.getItem();
            order.addItem(new OrderItem(item.getId(), item.getTitle(), item.getPrice(), cartItem.getCount()));
        }

        Order saved = orderRepository.save(order);
        cartItemRepository.deleteAllInBatch();
        return saved.getId();
    }
}
