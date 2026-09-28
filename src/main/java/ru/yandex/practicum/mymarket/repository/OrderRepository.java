package ru.yandex.practicum.mymarket.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.mymarket.model.Order;

/**
 * Репозиторий заказов покупателя.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Загружает заказ вместе со строками заказа (fetch join строк).
     *
     * @param id идентификатор заказа
     * @return заказ со строками (или пустой Optional)
     */
    @Query("select distinct o from Order o left join fetch o.items where o.id = :id")
    Optional<Order> findWithItemsById(@Param("id") Long id);

    /**
     * Загружает все заказы сразу со строками заказа.
     *
     * @return список заказов со строками
     */
    @Query("select distinct o from Order o left join fetch o.items")
    List<Order> findAllWithItems();
}
