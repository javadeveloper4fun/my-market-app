package ru.yandex.practicum.mymarket.dto;

import java.util.List;

/**
 * Страница корзины покупателя для шаблона Thymeleaf.
 *
 * <p>Содержит список позиций корзины и суммарную стоимость всех позиций.
 *
 * @param items позиции корзины с товарами и количеством
 * @param total суммарная стоимость всех позиций в корзине
 */
public record CartPage(List<ItemDto> items, long total) {}
