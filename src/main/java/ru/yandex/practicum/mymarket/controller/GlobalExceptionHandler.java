package ru.yandex.practicum.mymarket.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Глобальная обработка исключений контроллеров.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Превращает исключение «сущность не найдена» в ответ со статусом 404.
     *
     * @param e исключение о ненайденной сущности
     * @return ответ со статусом 404
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Возвращает 400 при ошибке конвертации параметра запроса (например,
     * невалидное значение enum Action). Обработчик обязателен, так как
     * {@link MethodArgumentTypeMismatchException} не является подтипом
     * {@link IllegalArgumentException}, но его первопричина (отказ конвертации
     * в тип параметра) — тоже IllegalArgumentException («No enum constant ...»):
     * резолвер {@code @ExceptionHandler} обходит цепочку {@link Throwable#getCause()},
     * и без этого метода невалидный action попал бы в {@link #handleNotFound}
     * и вернул бы 404 вместо 400.
     *
     * @param e исключение ошибки конвертации
     * @return ответ со статусом 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
