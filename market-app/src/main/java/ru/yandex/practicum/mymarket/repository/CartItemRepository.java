package ru.yandex.practicum.mymarket.repository;

import java.util.Collection;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.CartItem;

/**
 * Хранилище позиций корзины покупателя.
 *
 * <p>Все операции фильтруются по {@code user_id}: пользователь может видеть и
 * менять только свои позиции корзины. Это защита от IDOR — нельзя получить или
 * изменить чужую позицию по идентификатору товара.
 */
public interface CartItemRepository extends ReactiveCrudRepository<CartItem, Long> {

    /**
     * Возвращает позицию корзины по товару и пользователю.
     *
     * @param itemId идентификатор товара
     * @param userId идентификатор пользователя
     * @return позиция корзины или пустой результат
     */
    Mono<CartItem> findByItemIdAndUserId(Long itemId, Long userId);

    /**
     * Возвращает позиции корзины по переданным товарам и пользователю.
     *
     * @param itemIds идентификаторы товаров
     * @param userId  идентификатор пользователя
     * @return поток позиций этих товаров пользователя
     */
    Flux<CartItem> findAllByItemIdInAndUserId(Collection<Long> itemIds, Long userId);

    /**
     * Возвращает все позиции корзины пользователя.
     *
     * @param userId идентификатор пользователя
     * @return поток позиций корзины пользователя
     */
    @Query("SELECT * FROM cart_item WHERE user_id = :userId ORDER BY id")
    Flux<CartItem> findAllSortedByUserId(Long userId);

    /**
     * Увеличивает количество товара в позиции корзины на единицу.
     *
     * @param itemId идентификатор товара
     * @param userId идентификатор пользователя
     * @return число изменённых строк
     */
    @Modifying
    @Query("UPDATE cart_item SET count = count + 1 WHERE item_id = :itemId AND user_id = :userId")
    Mono<Integer> increaseCount(long itemId, Long userId);

    /**
     * Уменьшает количество товара в позиции корзины на оплаченное число.
     *
     * @param itemId идентификатор товара
     * @param count  оплаченное количество
     * @param userId идентификатор пользователя
     * @return число изменённых строк
     */
    @Modifying
    @Query("UPDATE cart_item SET count = count - :count WHERE item_id = :itemId AND user_id = :userId")
    Mono<Integer> decreaseCount(long itemId, int count, Long userId);

    /**
     * Удаляет позицию корзины, если её количество стало неположительным.
     *
     * @param itemId идентификатор товара
     * @param userId идентификатор пользователя
     * @return число удалённых строк
     */
    @Modifying
    @Query("DELETE FROM cart_item WHERE item_id = :itemId AND user_id = :userId AND count <= 0")
    Mono<Integer> deleteIfEmpty(long itemId, Long userId);

    /**
     * Удаляет позицию корзины по товару и пользователю.
     *
     * @param itemId идентификатор товара
     * @param userId идентификатор пользователя
     * @return число удалённых строк
     */
    @Modifying
    @Query("DELETE FROM cart_item WHERE item_id = :itemId AND user_id = :userId")
    Mono<Integer> deleteByItemId(long itemId, Long userId);
}
