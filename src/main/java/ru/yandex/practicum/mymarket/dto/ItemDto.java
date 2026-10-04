package ru.yandex.practicum.mymarket.dto;

/**
 * DTO товара для витрины. Неизменяемая запись с полями:
 * идентификатор, название, описание, путь к изображению,
 * цена в рублях и количество в корзине.
 *
 * <p>Поле {@code price} — цена в рублях. Точность до копеек сознательно
 * не поддерживается: постановка спринта 5 использует целые цены,
 * а шаблоны выполняют арифметику над {@code long}.
 *
 * @param id          идентификатор товара
 * @param title       название товара
 * @param description описание товара
 * @param imgPath     путь к изображению (без ведущего "/")
 * @param price       цена в рублях
 * @param count       количество в корзине (0 — товара нет в корзине)
 */
public record ItemDto(long id, String title, String description, String imgPath, long price, int count) {
    public static ItemDto fromEntity(ru.yandex.practicum.mymarket.model.Item item, int count) {
        return new ItemDto(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getImgPath(),
                item.getPrice().longValue(),
                count);
    }
}
