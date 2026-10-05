package ru.yandex.practicum.mymarket.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.yandex.practicum.mymarket.service.ItemNotFoundException;
import ru.yandex.practicum.mymarket.service.OrderNotFoundException;

/**
 * Глобальная обработка исключений контроллеров.
 *
 * <p>Каждый обработчик логирует исключение: без записи в лог ошибка, отвергнутая
 * запросом, не оставляла бы следов, и причину пришлось бы искать по косвенным
 * признакам — по неверному HTTP-статусу или по жалобе пользователя.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Превращает исключение «товар не найден» в ответ со статусом 404.
     *
     * @param e исключение о ненайденном товаре
     * @return ответ со статусом 404
     */
    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<Void> handleItemNotFound(ItemNotFoundException e) {
        LOG.debug("Запрошен несуществующий товар: {}", e.getMessage());
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
        LOG.debug("Запрошен несуществующий заказ: {}", e.getMessage());
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
        LOG.warn("Не удалось преобразовать параметр запроса '{}': {}", e.getName(), e.getMessage());
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
        LOG.warn("Некорректный аргумент запроса: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
