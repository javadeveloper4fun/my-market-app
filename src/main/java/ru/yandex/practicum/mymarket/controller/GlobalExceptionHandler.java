package ru.yandex.practicum.mymarket.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
 * <p>Отсутствие товара или заказа обрабатывается через {@code @ResponseStatus} на
 * самих исключениях — статус 404 и рендеринг страницы ошибки {@code error.html}.
 * Обработчик здесь не нужен: Spring сам рендерит вид, а не отдаёт пустое тело.
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
 *
 * <p>На WebFlux {@link org.springframework.web.multipart.MaxUploadSizeExceededException}
 * тоже не обрабатывается: это исключение сервлетного стека, а превышение
 * {@code spring.webflux.multipart.max-in-memory-size} приходит как
 * {@link org.springframework.core.io.buffer.DataBufferLimitException} и
 * ловится в {@link ImageStorageService#readBytes}.
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
     * <p>Ловит ошибки конвертации аргументов (например, нечисловой
     * {@code id} в {@code @PathVariable}): на WebFlux
     * {@link org.springframework.web.method.annotation.MethodArgumentTypeMismatchException}
     * не бросается, отказ конвертации приходит как {@link IllegalArgumentException}.
     *
     * <p>Обработчик намеренно широк: он ловит {@code IllegalArgumentException}
     * любого происхождения, в том числе и от ошибок в глубине сервисов.
     * Такая внутренняя ошибка будет отдана как 400 вместо 500 — это известное
     * ограничение. Альтернатива — отменить обработчик и я валидировать параметры
     * на границе контроллера, но тогда нечисловой {@code id} в {@code @PathVariable}
     * уйдёт в стандартный обработчик Spring Boot как 500, хотя это ошибка
     * клиента, а не сервера. Для спринта оставлен широкий обработчик с логом
     * на ERROR, чтобы подобные случаи были видны в логах.
     *
     * @param e исключение некорректного аргумента
     * @return ответ со статусом 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleIllegalArgument(IllegalArgumentException e) {
        LOG.error("Некорректный аргумент запроса: {}", e.getMessage());
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
}
