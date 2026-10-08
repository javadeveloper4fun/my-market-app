package ru.yandex.practicum.mymarket.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.ImageNotFoundException;
import ru.yandex.practicum.mymarket.service.ImageStorageService;

/**
 * Загрузка и отдача картинок товаров.
 *
 * <p>Отдельный контроллер, а не часть {@link ItemsController}, потому что это
 * самостоятельный эндпоинт контракта: постановка требует реализовать его
 * отдельно, и путь к картинке не обязан быть связан с каким-либо товаром.
 *
 * <p>Картинки отдаются через прокси, а не прямой ссылкой на хранилище. Причина
 * практическая: адрес хранилища известен только приложению, и выдавать его в
 * HTML значило бы отдавать посетителю детали развёртывания. Если завтра
 * хранилище переедет за другой адрес или потребует подпись на чтение, менять
 * придётся одно место, а не все шаблоны.
 */
@RestController
public class ImagesController {

    private static final Logger LOG = LoggerFactory.getLogger(ImagesController.class);

    private final ImageStorageService imageStorageService;

    public ImagesController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    /**
     * Загружает картинку и возвращает путь к ней.
     *
     * <p>Ответ — обычный текст с путём, а не JSON: путь кладётся в поле
     * {@code img_path} товара и дальше подставляется в шаблон как есть, так что
     * дополнительная обёртка только усложнила бы разбор ответа.
     *
     * <p>Параметры читаются как части запроса, а не как параметры строки: форма
     * отправляется с {@code multipart/form-data}, и в этом формате каждое поле
     * является частью. Обычный {@code @RequestParam} читает только строку запроса
     * и такое поле увидел бы как отсутствующее.
     *
     * @param image загружаемая картинка
     * @return путь к загруженной картинке вида {@code images/<ключ>}
     */
    @PostMapping("/images")
    public Mono<String> uploadImage(@RequestPart("image") FilePart image) {
        return imageStorageService.upload(image).map(key -> "images/" + key);
    }

    /**
     * Отдаёт картинку по ключу.
     *
     * <p>Шаблон строит адрес как {@code "/" + imgPath}, а в {@code img_path}
     * лежит {@code images/<ключ>}. Поэтому маршрут отдаёт ключ целиком, вместе с
     * префиксом {@code images/}, и перед обращением к хранилищу префикс снимается.
     * Так загруженные картинки и картинки сид-миграции обслуживаются одним
     * маршрутом, а применённые changeset'ы остаются нетронутыми.
     *
     * <p>Адрес картинки складывается из двух частей, и это стоит понимать, чтобы
     * не искать лишний префикс при отладке. В {@code img_path} товара лежит
     * {@code images/&lt;ключ&gt;}, а шаблон строит адрес как {@code "/" + imgPath}.
     * Вместе получается {@code /images/&lt;ключ&gt;}, где {@code images} — сегмент
     * самого маршрута, а не часть ключа. Поэтому в хранилище объект лежит под
     * именем {@code &lt;ключ&gt;}, и {@code images/} в пути не дублируется.
     *
     * <p>Именно так обслуживаются и картинки сид-миграции, и загруженные: формат
     * {@code img_path} у них одинаковый, поэтому маршрут один и уже применённые
     * changeset'ы остаются нетронутыми.
     *
     * <p>Путь разрешается жадным шаблоном {@code {*key}}, а не одиночным
     * сегментом: ключ может содержать слэши, если картинку положат в подкаталог,
     * и жадный шаблон переживёт это без изменения кода.
     *
     * <p>Жадный шаблон отдаёт значение <em>с ведущим слэшем</em>: на запрос
     * {@code /images/jacket.jpg} в {@code key} попадает {@code /jacket.jpg}.
     * Слэш снимается явно — без этого запрос уходит в хранилище за несуществующим
     * ключом, и картинка не отдаётся. Признак у такой ошибки молчаливый: ответ
     * 404 от хранилища выглядит как «картинки нет», а не как «адрес собран неверно».
     *
     * <p>Отсутствующий ключ отдаётся как 404, и решение принимается здесь, при
     * сборке ответа, а не обработчиком исключений. Поток с содержимым
     * подписывается уже после отправки статуса и заголовков, поэтому ошибка,
     * всплывшая при записи тела, статус ответа уже не изменит. Соответственно
     * читать хранилище нужно до того, как ответ построен: {@link Mono} от
     * {@link ImageStorageService#read(String)} подписывается здесь, и только затем
     * собирается {@link ResponseEntity}.
     *
     * @param key ключ объекта в хранилище с ведущим слэшем
     * @return поток содержимого картинки либо 404, если ключа нет
     */
    @GetMapping("/images/{*key}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> getImage(@PathVariable("key") String key) {
        String objectKey = key.startsWith("/") ? key.substring(1) : key;
        return imageStorageService
                .read(objectKey)
                .map(content -> ResponseEntity.ok()
                        .contentType(imageStorageService.contentTypeOf(objectKey))
                        .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)))
                        .body(content))
                .onErrorResume(ImageNotFoundException.class, error -> {
                    LOG.debug("Запрошена отсутствующая картинка: {}", objectKey);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }
}
