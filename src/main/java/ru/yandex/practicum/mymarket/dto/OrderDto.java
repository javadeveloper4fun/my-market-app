package ru.yandex.practicum.mymarket.dto;

import java.util.List;

public record OrderDto(long id, List<ItemDto> items, long totalSum) {
    public static OrderDto fromEntity(ru.yandex.practicum.mymarket.model.Order order) {
        List<ItemDto> items = order.getItems().stream()
                .map(oi -> new ItemDto(
                        oi.getItemId(), oi.getTitle(), "", "", oi.getPrice().longValue(), oi.getCount()))
                .toList();
        long totalSum = order.getItems().stream()
                .mapToLong(oi -> oi.getPrice().longValue() * oi.getCount())
                .sum();
        return new OrderDto(order.getId(), items, totalSum);
    }
}
