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
     * <p>Адрес картинки складывается из двух частей: в {@code img_path} товара
     * лежит {@code images/<ключ>}, а шаблон строит адрес как {@code "/" + imgPath}.
     * Вместе получается {@code /images/<ключ>}, где {@code images} — сегмент
     * маршрута, а не часть ключа, поэтому в хранилище объект лежит под именем
     * {@code <ключ>}. Сид-картинки и загруженные обслуживаются так же: формат
     * {@code img_path} у них одинаковый.
     *
     * <p>Пояснения к шагам стоят в коде метода.
     *
     * @param key ключ объекта в хранилище с ведущим слэшем
     * @return поток содержимого картинки либо 404, если ключа нет
     */
    @GetMapping("/images/{*key}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> getImage(@PathVariable("key") String key) {
        // Путь разрешается жадным шаблоном {*key}, а не одиночным сегментом:
        // ключ может содержать слэши, если картинку положат в подкаталог.
        // Жадный шаблон отдаёт значение с ведущим слэшем — на запрос
        // /images/jacket.jpg в key попадает /jacket.jpg, и слэш снимается здесь.
        // Без этого запрос ушёл бы в хранилище за несуществующим ключом, и
        // картинка не отдалась бы. Признак у такой ошибки молчаливый: ответ 404
        // от хранилища выглядит как «картинки нет», а не как «адрес собран неверно».
        String objectKey = key.startsWith("/") ? key.substring(1) : key;
        // Тема 3/11, урок 1/3 «Спецификация Reactive Streams». Это точка, где
        // подписка начинается: контроллер возвращает Mono, а вызывает subscribe()
        // на нём Spring WebFlux — при отправке ответа. Ниже по цепочке стоит
        // ImageStorageService.read, где ответ хранилища приходит отдельным
        // Publisher от AWS SDK и оборачивается в Flux. Между этими двумя точками
        // методов интерфейсов нет — обе стороны реализует Reactor, поэтому в коде
        // не видно ни подписки, ни Subscription. Разбор — в read(String).
        //
        // Отсутствующий ключ отдаётся как 404, и решение принимается здесь, при
        // сборке ответа, а не обработчиком исключений: поток с содержимым
        // подписывается уже после отправки статуса и заголовков, поэтому ошибка,
        // всплывшая при записи тела, статус ответа уже не изменит.
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
