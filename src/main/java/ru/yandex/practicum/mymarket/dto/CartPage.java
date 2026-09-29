package ru.yandex.practicum.mymarket.dto;

import java.util.List;

public record CartPage(List<ItemDto> items, long total) {}
