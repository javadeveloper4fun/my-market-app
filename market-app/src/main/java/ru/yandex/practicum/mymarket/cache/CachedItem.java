package ru.yandex.practicum.mymarket.cache;

import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.Item;

/**
 * Карточка товара в кеше.
 *
 * <p>В отличие от {@link ItemDto}, здесь нет количества в корзине. Корзина
 * меняется чаще каталога и не должна сбрасывать кеш товаров; количество
 * накладывается на карточку после чтения из кеша.
 *
 * @param id          идентификатор товара
 * @param title       название
 * @param description описание
 * @param imgPath     путь к изображению
 * @param price       цена в целых рублях
 */
public record CachedItem(long id, String title, String description, String imgPath, long price) {

    /**
     * Создаёт кешируемую карточку из сущности.
     *
     * @param item сущность товара
     * @return карточка для кеша
     */
    public static CachedItem fromEntity(Item item) {
        return new CachedItem(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getImgPath(),
                item.getPrice().longValue());
    }

    /**
     * Превращает карточку в DTO для шаблона.
     *
     * @param count количество в корзине
     * @return DTO товара
     */
    public ItemDto toDto(int count) {
        return new ItemDto(id, title, description, imgPath, price, count);
    }
}
