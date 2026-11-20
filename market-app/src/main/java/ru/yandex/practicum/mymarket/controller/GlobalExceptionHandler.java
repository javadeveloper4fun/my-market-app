package ru.yandex.practicum.mymarket.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import ru.yandex.practicum.mymarket.dto.InvalidItemFormException;
import ru.yandex.practicum.mymarket.service.ImageUploadException;

/**
 * Обработка исключений веб-слоя витрины.
 *
 * <p>Обработчики здесь покрывают ошибки границы запроса и прикладные ошибки
 * пользовательского ввода, для которых нужен предсказуемый HTTP-ответ. Доменные
 * случаи «ресурс не найден» размечены {@code @ResponseStatus} на самих
 * исключениях и обрабатываются стандартным механизмом Spring Boot/WebFlux;
 * отдельный маппинг для них здесь не нужен.
 *
 * <p>Ошибки чтения и привязки входных данных ({@link ServerWebInputException},
 * {@link WebExchangeBindException}) возвращаются как 400: это некорректный
 * запрос клиента (неверный тип или формат параметра, отсутствующее обязательное
 * значение и т.п.). Внутренние ошибки приложения не сводятся к этому обработчику,
 * чтобы программные дефекты не маскировались под ошибку ввода.
 *
 * <p>Для ошибок заполнения формы товара и загрузки изображения возвращается 400
 * с текстом причины: он нужен как обратная связь пользователю. Остальные
 * необработанные исключения проходят стандартную обработку ошибок Spring и не
 * преобразуются здесь в 400.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Возвращает 400 для некорректного входного запроса.
     *
     * <p>Сюда попадают ошибки разбора и привязки веб-параметров, которые WebFlux
     * представляет как {@link ServerWebInputException} или
     * {@link WebExchangeBindException} (например, неверный тип параметра или
     * отсутствующее обязательное значение).
     *
     * @param e ошибка входного запроса
     * @return ответ со статусом 400
     */
    @ExceptionHandler({ServerWebInputException.class, WebExchangeBindException.class})
    public ResponseEntity<Void> handleBadRequest(Exception e) {
        LOG.warn("Некорректный запрос: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    /**
     * Возвращает 400 с текстом причины при неверно заполненной форме товара.
     *
     * @param e ошибка валидации формы
     * @return ответ со статусом 400 и текстом причины
     */
    @ExceptionHandler(InvalidItemFormException.class)
    public ResponseEntity<String> handleInvalidItemForm(InvalidItemFormException e) {
        LOG.warn("Форма добавления товара не принята: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /**
     * Возвращает 400 с текстом причины, если изображение товара не принято.
     *
     * @param e ошибка загрузки или проверки изображения
     * @return ответ со статусом 400 и текстом причины
     */
    @ExceptionHandler(ImageUploadException.class)
    public ResponseEntity<String> handleImageUpload(ImageUploadException e) {
        LOG.warn("Изображение товара не принято: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
