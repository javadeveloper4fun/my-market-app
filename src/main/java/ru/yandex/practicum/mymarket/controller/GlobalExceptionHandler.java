package ru.yandex.practicum.mymarket.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import ru.yandex.practicum.mymarket.dto.InvalidItemFormException;
import ru.yandex.practicum.mymarket.service.ImageUploadException;

/**
 * Глобальная обработка исключений контроллеров.
 *
 * <p>Каждый обработчик логирует исключение: без записи в лог ошибка, отвергнутая
 * запросом, не оставляла бы следов, и причину пришлось бы искать по косвенным
 * признакам — по неверному HTTP-статусу или по жалобе пользователя.
 *
 * <p>Отказ в пользовательском вводе отдаётся с текстом причины, а не пустым
 * телом: посетитель должен видеть, что именно исправить. Проверки формы
 * добавления товара без этого давали 500 — незаполненная цена
 * распаковывалась из обёртки в примитив и роняла запрос.
 *
 * <p>Отсутствие товара или заказа здесь не разбирается намеренно. Статус 404 задан
 * на самих исключениях через {@code @ResponseStatus}, и ответ уходит в штатную
 * обработку ошибок Spring, которая отрисовывает {@code templates/error/error.html}.
 * Имя каталога с {@code error} внутри не опечатка: в Spring Boot 4 обработчик
 * ошибок ищет вид по именам {@code error/<статус>}, {@code error/<серия>} и
 * {@code error/error}, и шаблон с плоским именем {@code error.html} не будет
 * найден вовсе — в этом случае отдаётся дефолтная страница Spring.
 * Обработчик отвечал бы пустым телом: страницу с ошибкой он всё равно не
 * отрисовал бы, а посетитель получил бы пустую страницу вместо объяснения.
 *
 * <p>На WebFlux {@link org.springframework.web.method.annotation.MethodArgumentTypeMismatchException}
 * намеренно не обрабатывается: отказ конвертации приходит как
 * {@link IllegalArgumentException} и ловится {@link #handleIllegalArgument}.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    /**
     * Отдаёт 400 с текстом причины, если поля формы заполнены неверно.
     *
     * <p>Текст ответа — часть контракта: пользователь должен видеть, какое
     * поле не заполнено, иначе форма отклоняется молча.
     *
     * @param e исключение о неверных полях формы
     * @return ответ со статусом 400 и текстом причины
     */
    @ExceptionHandler(InvalidItemFormException.class)
    public ResponseEntity<String> handleInvalidItemForm(InvalidItemFormException e) {
        LOG.warn("Форма добавления товара не принята: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /**
     * Возвращает 400 с текстом причины, когда картинка товара не принята.
     *
     * <p>Отдельный обработчик нужен ради текста: общий обработчик некорректного
     * аргумента отвечает пустым телом, и пользователь увидел бы просто «400» без
     * объяснения, в чём именно не так с его картинкой.
     *
     * <p>Причина отдаётся и в ответе, и в лог. В ответе она нужна человеку, в лог
     * — для разбора случаев, когда форму заполняли мимо интерфейса.
     *
     * @param e исключение о непринятой картинке
     * @return ответ со статусом 400 и текстом причины
     */
    @ExceptionHandler(ImageUploadException.class)
    public ResponseEntity<String> handleImageUpload(ImageUploadException e) {
        LOG.warn("Картинка товара не принята: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /**
     * Возвращает 400, когда запрос превышает допустимый размер файла.
     *
     * <p>Размер задан настройкой {@code spring.servlet.multipart.max-file-size}.
     * Файл большего размера отбрасывается при разборе запроса, до контроллера,
     * и его отказ приходит с типом, который ни один из обработчиков
     * контроллеров не ловит. Без этого обработчика пользователь получил бы 500
     * на действие, которое он может исправить сам.
     *
     * @param e исключение разбора запроса с превышенным размером
     * @return ответ со статусом 400 и текстом причины
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        LOG.warn("Файл превышает допустимый размер: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Файл слишком большой. Допустимый размер — 5 МБ.");
    }
}
