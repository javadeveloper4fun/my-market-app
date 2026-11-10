package ru.yandex.practicum.mymarket.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Запрошенная сущность не найдена.
 *
 * <p>Выделена в отдельный тип, чтобы «не найдено» нельзя было перепутать с некорректным
 * аргументом. Если бы оба случая бросали {@code IllegalArgumentException}, обработчик
 * отдавал бы на настоящую ошибку в аргументе тоже 404 вместо 400.
 *
 * <p>Статус задан на самом исключении, а не в обработчике, и это не stylistic
 * выбор. Обработчик {@code @ExceptionHandler} собирает ответ сам и на этом
 * останавливается: страница с ошибкой отрисована не будет, и вместо неё
 * уйдёт пустой ответ со статусом 404. С {@code @ResponseStatus} исключение
 * доходит до штатной обработки ошибок Spring, которая рендерит
 * {@code templates/error/error.html}, — посетитель получает страницу, а не
 * пустоту. Подробнее об имени шаблона — в {@code GlobalExceptionHandler}.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ItemNotFoundException extends RuntimeException {

    /**
     * @param itemId идентификатор ненайденного товара
     */
    public ItemNotFoundException(long itemId) {
        super("Item not found: " + itemId);
    }
}
