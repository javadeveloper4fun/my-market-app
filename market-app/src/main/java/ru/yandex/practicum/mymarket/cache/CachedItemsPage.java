package ru.yandex.practicum.mymarket.cache;

import java.util.List;

/**
 * Страница каталога в кеше.
 *
 * <p>Количество в корзине здесь отсутствует по той же причине, что и в
 * {@link CachedItem}: оно относится к другой изменяемой сущности.
 *
 * @param items товары страницы в порядке выдачи
 * @param total общее число товаров под запрос
 */
public record CachedItemsPage(List<CachedItem> items, long total) {}
