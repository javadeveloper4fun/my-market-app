package ru.yandex.practicum.mymarket.dto;

/**
 * Проекция количества товара в корзине.
 *
 * <p>Это интерфейс-проекция Spring Data (closed projection): Spring Data
 * создаёт прокси, реализующий интерфейс, а значения методов берутся
 * из алиасов JPQL-запроса. Запрос в {@code CartItemRepository.findCountsByItemIds}
 * возвращает две скалярные колонки с алиасами {@code itemId} и {@code itemCount},
 * которые совпадают с именами геттеров (getItemId, getItemCount). Конструктор
 * у интерфейса отсутствует, поэтому выражение {@code select new ...} здесь
 * неприменимо — это не DTO-проекция.
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
