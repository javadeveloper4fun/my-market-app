package ru.yandex.practicum.mymarket.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.yandex.practicum.mymarket.service.ItemNotFoundException;
import ru.yandex.practicum.mymarket.service.OrderNotFoundException;

/**
 * Глобальная обработка исключений контроллеров.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Превращает исключение «товар не найден» в ответ со статусом 404.
     *
     * @param e исключение о ненайденном товаре
     * @return ответ со статусом 404
     */
    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<Void> handleItemNotFound(ItemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Превращает исключение «заказ не найден» в ответ со статусом 404.
     *
     * @param e исключение о ненайденном заказе
     * @return ответ со статусом 404
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Void> handleOrderNotFound(OrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Возвращает 400 при ошибке конвертации параметра запроса (например,
     * невалидное значение enum Action). Обработчик обязателен, так как
     * {@link MethodArgumentTypeMismatchException} не является подтипом
     * {@link IllegalArgumentException}, но его первопричина (отказ конвертации
     * в тип параметра) — тоже IllegalArgumentException («No enum constant ...»):
     * резолвер {@code @ExceptionHandler} обходит цепочку {@link Throwable#getCause()},
     * и без этого метода невалидный action попал бы в {@link #handleIllegalArgument}
     * и вернул бы 404 вместо 400.
     *
     * @param e исключение ошибки конвертации
     * @return ответ со статусом 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    /**
     * Возвращает 400 при некорректном аргументе, переданном в метод контроллера.
     *
     * <p>Обработчик объявлен отдельным типом, а не общим для «не найдено»: когда оба случая
     * отдавали 404, любая настоящая ошибка в аргументе тоже превращалась в 404 и выглядела
     * как успешный запрос к отсутствующему ресурсу.
     *
     * @param e исключение некорректного аргумента
     * @return ответ со статусом 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
