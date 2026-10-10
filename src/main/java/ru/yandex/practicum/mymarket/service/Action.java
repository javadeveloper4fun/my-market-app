package ru.yandex.practicum.mymarket.service;

/**
 * Действие над позицией корзины.
 */
public enum Action {
    /** Уменьшить количество товара на единицу. */
    MINUS,
    /** Увеличить количество товара на единицу. */
    PLUS,
    /** Удалить товар из корзины. */
    DELETE
}
