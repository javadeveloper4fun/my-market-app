package ru.yandex.practicum.mymarket.service;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.config.ImageStorageProperties;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Хранение картинок товаров в S3-совместимом хранилище.
 *
 * <p>Ключ объекта строится из случайного идентификатора и расширения исходного
 * файла. Имя, заданное пользователем, в ключ не попадает намеренно: в путь
 * объекта любое пользовательское значение попадать не должно, иначе имя вида
 * {@code ../../etc/passwd} или с пробелами пришлось бы отдельно очищать.
 * Случайное имя снимает вопрос целиком, а исходное имя при необходимости можно
 * хранить отдельно как метаданные объекта.
 *
 * <p>Клиент асинхронный, и чтение отдаётся потоком {@link DataBuffer}: картинка
 * не собирается целиком в память перед выдачей. Для небольших файлов разница
 * невелика, но потоковая выдача — единственный способ не упереться в память на
 * большом изображении, и переделывать это потом дороже.
 *
 * <p>Часть запроса принимается типом {@link FilePart}, а не {@code MultipartFile}:
 * для WebFlux моста к {@code MultipartFile} нет, и попытка объявить такой
 * параметр заканчивается 415 при любом содержимом запроса. {@code Part} —
 * нативный реактивный тип, его содержимое приходит потоком буферов, и блокирующее
 * чтение файла в этой цепочке не требуется вовсе.
 */
@Service
public class ImageStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(ImageStorageService.class);

    /**
     * Расширения, которые принимаются при загрузке.
     *
     * <p>Список ограничен не для безопасности, а потому что тип файла нужен
     * дальше: при отдаче картинки он попадает в заголовок {@code Content-Type}.
     * Принимая что угодно, пришлось бы либо доверять расширению при формировании
     * типа ответа, либо хранить тип рядом с ключом. Для витрины хватает трёх
     * форматов, которые браузер показывает без оговорок.
     */
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif");

    /**
     * Код ответа хранилища, которым оно сообщает об отсутствии объекта.
     *
     * <p>Вынесен в константу, потому что сравнивается с кодом ответа AWS SDK,
     * а не с типом исключения: значение 404 не требует пояснений при чтении
     * условия.
     */
    private static final int NOT_FOUND_STATUS = 404;

    /**
     * Фабрика буферов для оборачивания байтов хранилища.
     *
     * <p>Объявлена через интерфейс {@link DataBufferFactory}, а не напрямую
     * {@link DefaultDataBufferFactory}: метод {@code wrap} у конкретного класса
     * объявлен с возвратом этого же конкретного типа, и поток получался бы
     * {@code Flux<DefaultDataBuffer>} — такой поток нельзя вернуть как
     * {@code Flux<DataBuffer>}, а параметризованные типы в Reactor инвариантны.
     */
    private static final DataBufferFactory BUFFER_FACTORY = DefaultDataBufferFactory.sharedInstance;

    private final S3AsyncClient s3Client;
    private final ImageStorageProperties properties;

    public ImageStorageService(S3AsyncClient s3Client, ImageStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /**
     * Загружает картинку в хранилище.
     *
     * @param image загружаемая картинка
     * @return ключ созданного объекта
     */
    public Mono<String> upload(FilePart image) {
        return Mono.fromCallable(() -> buildKey(image.filename()))
                .flatMap(key -> checkSize(image).thenReturn(key))
                .flatMap(key -> readBytes(image)
                        .flatMap(bytes -> Mono.fromFuture(s3Client.putObject(
                                        putRequest(key, image.filename()), AsyncRequestBody.fromBytes(bytes)))
                                .thenReturn(key)))
                .doOnNext(key -> LOG.info("Картинка загружена в хранилище: {}", key));
    }

    /**
     * Отдаёт содержимое объекта потоком.
     *
     * <p>Содержимое приходит из хранилища потоком {@link ByteBuffer}, а не
     * собранным ответом: {@link AsyncResponseTransformer#toPublisher()} отдаёт
     * данные по мере поступления, и картинка не занимает память целиком, пока
     * браузер её ещё не получил.
     *
     * <p>Метод возвращает {@link Mono}, а не готовый {@link Flux}, и это сделано
     * ради кода ответа на отсутствующий объект. Поток с содержимым подписывается
     * уже после отправки статуса и заголовков, поэтому ошибка «объекта нет»
     * возникла бы слишком поздно: клиент получил бы 200 с оборванным телом
     * вместо 404. Запрос к хранилищу поэтому выполняется при подписке на
     * {@link Mono}, то есть ещё до того, как ответ собран.
     *
     * <p>Отсутствующий объект превращается в {@link ImageNotFoundException}:
     * хранилище отвечает на него кодом 404, и отличить это от сбоя хранилища
     * можно только по коду ответа — без такого разделения пропажа картинки
     * отдавалась бы как 500 и засоряла лог инцидентами о несуществующем ключе.
     *
     * <p>Тема 2/11 «Реактивное программирование на Java», п. «Холодный поток»:
     * {@code Mono.defer} откладывает запрос к хранилищу до подписки, поэтому
     * поток холодный — без подписчика сетевой вызов не выполняется вовсе.
     * Плюс к этому же пункту: содержимое читается потоком буферов, а не
     * собранным ответом, что и есть обратное давление на практике — данные
     * идут частями по мере готовности подписчика.
     *
     * @param key ключ объекта
     * @return поток буферов с содержимым картинки
     */
    public Mono<Flux<DataBuffer>> read(String key) {
        return Mono.defer(() -> Mono.fromFuture(s3Client.getObject(
                        GetObjectRequest.builder()
                                .bucket(properties.getBucket())
                                .key(key)
                                .build(),
                        AsyncResponseTransformer.toPublisher())))
                .map(publisher -> Flux.from(publisher).map(buffer -> BUFFER_FACTORY.wrap(buffer)))
                .onErrorResume(
                        S3Exception.class,
                        error -> isNotFound(error) ? Mono.error(new ImageNotFoundException(key)) : Mono.error(error));
    }

    /**
     * Определяет, означает ли ошибка хранилища отсутствие объекта.
     *
     * <p>Проверяются и тип исключения, и код ответа: часть хранилищ отдаёт
     * {@code NoSuchKey}, часть — общий {@code S3Exception} со статусом 404.
     * Сверка только с одним из них оставила бы вторую разновидность ошибкой 500.
     *
     * @param error ошибка хранилища
     * @return {@code true}, если объекта с таким ключом нет
     */
    private boolean isNotFound(S3Exception error) {
        return error instanceof NoSuchKeyException || error.statusCode() == NOT_FOUND_STATUS;
    }

    /**
     * Удаляет объект как компенсирующее действие.
     *
     * <p>Если вставка товара в базу не удалась, положенный объект убирается:
     * иначе в хранилище копились бы картинки, на которые никто не ссылается.
     *
     * <p>Ошибка удаления не поднимается наружу и только логируется. Вызывающий
     * код уже разбирает исходную неудачу, вторая ошибка из поверхности удаления
     * её бы замаскировала; осиротевший объект работе приложения не мешает.
     *
     * @param key ключ объекта
     * @return завершение удаления
     */
    public Mono<Void> delete(String key) {
        return Mono.fromFuture(s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .build()))
                .doOnSuccess(ignored -> LOG.info("Картинка удалена из хранилища: {}", key))
                .onErrorResume(error -> {
                    LOG.warn("Не удалось удалить картинку {}: {}", key, error.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /**
     * Возвращает тип содержимого по расширению ключа.
     *
     * @param key ключ объекта
     * @return тип содержимого для заголовка ответа
     */
    public MediaType contentTypeOf(String key) {
        return switch (extensionOf(key)) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            default -> MediaType.IMAGE_JPEG;
        };
    }

    /**
     * Собирает ключ объекта из случайного идентификатора и расширения файла.
     *
     * @param originalFilename имя загруженного файла
     * @return ключ объекта
     * @throws ImageUploadException если расширение отсутствует или недопустимо
     */
    private String buildKey(String originalFilename) {
        String extension = extensionOf(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ImageUploadException(
                    "Недопустимый формат изображения. Ожидаются: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
        return UUID.randomUUID().toString() + "." + extension;
    }

    /**
     * Извлекает расширение из имени файла в нижнем регистре.
     *
     * @param filename имя файла
     * @return расширение без точки; пустая строка, если расширения нет
     */
    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Проверяет размер картинки до чтения содержимого.
     *
     * <p>Проверка своя, а не только настройкой сервера отправки. Каркас
     * отсекает слишком большой файл сам и отвечает ошибкой разбора запроса, а
     * здесь можно отказать понятным текстом: пользователь узнает, что именно
     * превышено, а не получит ошибку без указания причины.
     *
     * <p>Если размер в заголовках неизвестен, проверка пропускается: это обычно
     * означает потоковую отправку, и длина вычисляется по мере чтения.
     *
     * @param image часть запроса с файлом
     * @return завершение проверки
     */
    private Mono<Void> checkSize(FilePart image) {
        Long declaredLength = image.headers().getContentLength();
        long limit = parseSize(properties.getMaxUploadSize());
        if (declaredLength != null && declaredLength > limit) {
            return Mono.error(
                    new ImageUploadException("Файл слишком большой: допустимо до " + (limit / 1024 / 1024) + " МБ"));
        }
        return Mono.empty();
    }

    /**
     * Разбирает размер вида {@code 5MB} в байты.
     *
     * <p>Разбирается вручную, а не через {@code DataSize}, потому что
     * свойство приходит строкой из конфигурации, и молчаливая подстановка
     * нуля обрекла бы файл любого размера без предупреждения.
     *
     * @param size размер с суффиксом единицы
     * @return размер в байтах; при неразобранном значении — 1 МБ
     */
    private long parseSize(String size) {
        if (size == null || size.isBlank()) {
            return 1024L * 1024L;
        }
        String trimmed = size.trim().toUpperCase(Locale.ROOT);
        long multiplier =
                switch (trimmed.substring(Math.max(0, trimmed.length() - 2))) {
                    case "KB" -> 1024L;
                    case "GB" -> 1024L * 1024L * 1024L;
                    default -> 1024L * 1024L;
                };
        String digits = trimmed.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? multiplier : Long.parseLong(digits) * multiplier;
    }

    /**
     * Читает содержимое части запроса целиком.
     *
     * <p>Содержимое приходит потоком буферов и для загрузки собирается в один
     * массив: блочный {@code RequestBody} не умеет отдавать тело частями, а
     * асинхронный потребовал бы удерживать части открытыми всё время передачи.
     * Размер ограничен настройкой {@code spring.webflux.multipart.max-in-memory-size},
     * поэтому собирать в память здесь безопасно.
     *
     * @param image часть запроса с файлом
     * @return содержимое файла
     */
    private Mono<byte[]> readBytes(FilePart image) {
        return DataBufferUtils.join(image.content()).map(buffer -> {
            try {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                return bytes;
            } finally {
                DataBufferUtils.release(buffer);
            }
        });
    }

    /**
     * Собирает запрос на запись объекта.
     *
     * @param key      ключ объекта
     * @param fileName имя исходного файла; используется как тип содержимого
     * @return запрос на запись
     */
    private PutObjectRequest putRequest(String key, String fileName) {
        return PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(contentTypeOf(key).toString())
                .build();
    }
}
