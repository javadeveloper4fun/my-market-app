package ru.yandex.practicum.mymarket.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
     * Проверяет, есть ли товар с указанным идентификатором уже в корзине.
     *
     * @param itemId идентификатор товара
     * @return {@code true}, если товар уже в корзине
     */
    boolean existsByItemId(Long itemId);
}
