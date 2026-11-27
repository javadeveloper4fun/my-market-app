package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.Order;

/**
 * Хранилище заказов покупателя.
 *
 * <p>Все операции фильтруются по {@code user_id}: пользователь может видеть
 * только свои заказы. Это защита от IDOR — нельзя получить чужой заказ по
 * идентификатору.
 */
public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {

    /**
     * Возвращает идентификаторы всех заказов пользователя.
     *
     * @param userId идентификатор пользователя
     * @return поток идентификаторов заказов пользователя
     */
    @Query("SELECT id FROM orders WHERE user_id = :userId ORDER BY id DESC")
    Flux<Long> findAllIdsByUserId(Long userId);

    /**
     * Возвращает все заказы пользователя вместе со статусами.
     *
     * @param userId идентификатор пользователя
     * @return поток заказов в порядке убывания идентификатора
     */
    Flux<Order> findAllByUserIdOrderByIdDesc(Long userId);

    /**
     * Возвращает заказ по идентификатору и пользователю.
     *
     * @param id     идентификатор заказа
     * @param userId идентификатор пользователя
     * @return заказ или пустой результат, если заказа нет или он принадлежит другому пользователю
     */
    @Query("SELECT * FROM orders WHERE id = :id AND user_id = :userId")
    Mono<Order> findByIdAndUserId(Long id, Long userId);

    /**
     * Находит заказ по идентификатору попытки оформления.
     *
     * @param attemptId идентификатор попытки
     * @return заказ или пустой результат
     */
    Mono<Order> findByAttemptId(String attemptId);

    /**
     * Меняет статус заказа только если он ещё находится в ожидаемом состоянии.
     *
     * @param id            идентификатор заказа
     * @param newStatus     новый статус
     * @param expectedStatus ожидаемый текущий статус
     * @return число обновлённых строк
     */
    @Modifying
    @Query("UPDATE orders SET status = :newStatus WHERE id = :id AND status = :expectedStatus")
    Mono<Integer> transitionStatus(
            @Param("id") Long id, @Param("newStatus") String newStatus, @Param("expectedStatus") String expectedStatus);
}
