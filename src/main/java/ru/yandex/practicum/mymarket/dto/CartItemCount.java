package ru.yandex.practicum.mymarket.dto;

/**
 * Проекция количества товара в корзине.
 *
 * <p>Интерфейс используется Spring Data как источник данных: значения колонок
 * {@code itemId} и {@code itemCount} подставляются по псевдонимам из JPQL-запроса.
 */
public interface CartItemCount {

    /**
     * @return идентификатор товара
     */
    Long getItemId();

    /**
     * @return количество единиц товара в корзине
     */
    Integer getItemCount();
}
