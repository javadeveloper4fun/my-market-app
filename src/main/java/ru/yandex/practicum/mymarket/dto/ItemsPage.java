package ru.yandex.practicum.mymarket.dto;

import java.util.ArrayList;
import java.util.List;

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
