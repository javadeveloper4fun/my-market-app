package ru.yandex.practicum.mymarket.service;

/**
 * Запрошенная сущность не найдена.
 *
 * <p>Выделена в отдельный тип, чтобы «не найдено» нельзя было перепутать с некорректным
 * аргументом. Если бы оба случая бросали {@code IllegalArgumentException}, обработчик
 * отдавал бы на настоящую ошибку в аргументе тоже 404 вместо 400.
 */
public class ItemNotFoundException extends RuntimeException {

    /**
     * @param itemId идентификатор ненайденного товара
     */
    public ItemNotFoundException(long itemId) {
        super("Item not found: " + itemId);
    }
}
