package ru.yandex.practicum.mymarket.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Страница витрины товаров для шаблона Thymeleaf.
 *
 * <p>Содержит товары, сгруппированные по строкам (по три товара в строке), а также
 * параметры поиска, сортировки и состояние постраничной навигации. Недостающие
 * ячейки в последней строке заполняются заглушками {@code ItemDto(-1, ...)}.
 *
 * @param rows  строки товара для отображения в таблице витрины
 * @param search строка поиска, которая пришла в запросе
 * @param sort   способ сортировки, который пришёл в запросе
 * @param paging состояние постраничной навигации
 */
public record ItemsPage(List<List<ItemDto>> rows, String search, String sort, Paging paging) {
    public static ItemsPage of(List<ItemDto> items, String search, String sort, Paging paging) {
        List<List<ItemDto>> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i += 3) {
            List<ItemDto> row = new ArrayList<>(items.subList(i, Math.min(i + 3, items.size())));
            while (row.size() < 3) {
                row.add(new ItemDto(-1, "", "", "", 0, 0));
            }
            rows.add(row);
        }
        return new ItemsPage(rows, search, sort, paging);
    }
}
