package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import ru.yandex.practicum.mymarket.model.Order;

/**
 * Хранилище заказов покупателя.
 *
 * <p>Методов {@code findWithItemsById} и {@code findAllWithItems} здесь нет, и это
 * главное отличие от блокирующей версии. Оба держались на
 * {@code left join fetch} и {@code distinct}: join размножал строку заказа по
 * числу позиций, а {@code distinct} это маскировал. В R2DBC join-fetch не
 * применяется, поэтому позиции загружаются отдельно — см.
 * {@link OrderItemRepository#findAllByOrderIdIn}.
 */
public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {

    /**
     * Возвращает идентификаторы всех заказов.
     *
     * <p>Для списка заказов нужны только идентификаторы: всё остальное приходит
     * из позиций. Запрашивать сущности здесь означало бы загрузить позиции на
     * каждый заказ, то есть получить N+1.
     *
     * @return поток идентификаторов заказов
     */
    @Query("SELECT id FROM orders")
    Flux<Long> findAllIds();
}
