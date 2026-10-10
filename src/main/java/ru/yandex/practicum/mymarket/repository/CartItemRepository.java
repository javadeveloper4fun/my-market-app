package ru.yandex.practicum.mymarket.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.mymarket.dto.CartItemCount;
import ru.yandex.practicum.mymarket.model.CartItem;

/**
 * Репозиторий позиций корзины покупателя.
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * Возвращает все позиции корзины сразу с привязанными товарами
     * (fetch join позволяет избежать проблемы N+1 при выводе корзины).
     *
     * @return список позиций корзины
     */
    @Query("select ci from CartItem ci join fetch ci.item")
    List<CartItem> findAllWithItem();

    /**
     * Возвращает позицию корзины по идентификатору товара.
     *
     * @param itemId идентификатор товара
     * @return позиция корзины (или пустая, если товара в корзине нет)
     */
    Optional<CartItem> findByItemId(Long itemId);

    /**
     * Возвращает количество единиц товара в корзине для переданных идентификаторов.
     *
     * <p>Справочный запрос для витрины: нужны только идентификатор товара и количество, поэтому
     * позиции корзины выбираются двумя скалярными колонками без загрузки самих {@code CartItem}
     * и без join к {@code Item}. Это ограничивает выборку размером страницы товаров, а не
     * размером всей корзины.
     *
     * @param itemIds идентификаторы товаров текущей страницы
     * @return список проекций с идентификатором товара и его количеством
     */
    @Query("select ci.item.id as itemId, ci.count as itemCount from CartItem ci where ci.item.id in :itemIds")
    List<CartItemCount> findCountsByItemIds(@Param("itemIds") Collection<Long> itemIds);
}
