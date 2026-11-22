package ru.yandex.practicum.mymarket.repository;

import java.util.Collection;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import ru.yandex.practicum.mymarket.model.OrderItem;

/**
 * Хранилище позиций заказа.
 *
 * <p>Отдельный репозиторий нужен потому, что R2DBC не сохраняет вложенные
 * сущности: заказ и его позиции пишутся разными вызовами, и колонку
 * {@code order_id} в позиции проставляет вызывающий код.
 */
public interface OrderItemRepository extends ReactiveCrudRepository<OrderItem, Long> {

    /**
     * Возвращает позиции заказов из переданного списка.
     *
     * <p>Проекция вместо сущностей здесь заводить не стоит: интерфейс-проекция в
     * R2DBC сопоставляет столбцы по стратегии именования, и при несовпадении
     * геттер возвращает {@code null} без ошибки. Обычный derived-запрос к
     * сущности работает штатно.
     *
     * @param orderIds идентификаторы заказов
     * @return поток позиций этих заказов
     */
    Flux<OrderItem> findAllByOrderIdIn(Collection<Long> orderIds);
}
