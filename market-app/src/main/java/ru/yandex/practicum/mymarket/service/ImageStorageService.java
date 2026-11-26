package ru.yandex.practicum.mymarket.service;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
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
     * Проверяет корректность {@code market.image-s3.max-upload-size} при старте приложения.
     * Конфигурационная ошибка должна падать громко при старте, а не отдавать 400 пользователю
     * при первой загрузке картинки.
     */
    @PostConstruct
    private void validateMaxUploadSize() {
        try {
            parseSize(properties.getMaxUploadSize());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Некорректная настройка market.image-s3.max-upload-size: " + properties.getMaxUploadSize(), ex);
        }
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
                        .flatMap(bytes -> Mono.fromFuture(
                                        s3Client.putObject(putRequest(key), AsyncRequestBody.fromBytes(bytes)))
                                .thenReturn(key)))
                .doOnNext(key -> LOG.info("Картинка загружена в хранилище: {}", key));
    }

    /**
     * Возвращает содержимое объекта потоком.
     *
     * <p>Содержимое приходит из хранилища потоком {@link ByteBuffer}, а не
     * собранным ответом: картинка не занимает память целиком, пока браузер
     * её ещё не получил.
     *
     * <p>Метод возвращает {@link Mono}, а не готовый {@link Flux}, и это сделано
     * ради кода ответа на отсутствующий объект. Поток с содержимым подписывается
     * уже после отправки статуса и заголовков, поэтому ошибка «объекта нет»
     * возникла бы слишком поздно: клиент получил бы 200 с оборванным телом
     * вместо 404.
     *
     * @param key ключ объекта
     * @return поток буферов с содержимым картинки
     */
    public Mono<Flux<DataBuffer>> read(String key) {
        // Тема 2/11, урок 1/3 «Спецификация Reactive Streams».
        // Mono.defer откладывает запрос к хранилищу до подписки: пока ответ не
        // отправляют, сетевого вызова не происходит вовсе.
        return Mono.defer(() -> Mono.fromFuture(s3Client.getObject(
                        GetObjectRequest.builder()
                                .bucket(properties.getBucket())
                                .key(key)
                                .build(),
                        // Тема 3/11, урок 1/3 «Спецификация Reactive Streams».
                        // Из четырёх интерфейсов спецификации здесь используется
                        // только Publisher — источник данных. Его отдаёт AWS SDK,
                        // то есть издателем выступает HTTP-клиент хранилища.
                        AsyncResponseTransformer.toPublisher())))
                // Flux.from оборачивает этот Publisher в тип Reactor. Сама
                // подписка происходит на стороне каркаса в момент отправки
                // ответа, поэтому явного subscribe() в коде нет.
                .map(publisher -> Flux.from(publisher).map(buffer -> BUFFER_FACTORY.wrap(buffer)))
                // Отсутствующий объект превращается в ImageNotFoundException:
                // хранилище отвечает на него кодом 404, и отличить это от сбоя
                // хранилища можно только по коду ответа. Без такого разделения
                // пропажа картинки отдавалась бы как 500 и засоряла лог
                // инцидентами о несуществующем ключе.
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
     * @see #ALLOWED_EXTENSIONS для допустимых расширений (jpg/jpeg/png/gif → image/*, иначе application/octet-stream)
     * <p>Неизвестное расширение (например, {@code .txt}) даёт {@link MediaType#APPLICATION_OCTET_STREAM},
     * а не {@link MediaType#IMAGE_JPEG}: доверять расширению при формировании
     * {@code Content-Type} нельзя — файл с расширением {@code .jpg} может быть чем угодно,
     * и браузер не должен пытаться отрендерить его как картинку.
     */
    public MediaType contentTypeOf(String key) {
        return switch (extensionOf(key)) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
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
     * Возвращает фактический лимит размера изображения в байтах.
     *
     * @return лимит в байтах
     */
    long maxUploadSizeBytes() {
        return parseSize(properties.getMaxUploadSize());
    }

    /**
     * Проверяет размер картинки по заголовку {@code Content-Length}, если он указан.
     *
     * <p>Заголовок может отсутствовать или быть заниженным, поэтому эта проверка
     * только быстрый отказ до чтения. Фактический размер контролируется при чтении
     * содержимого в {@link #readBytes(FilePart)}.
     *
     * @param image часть запроса с файлом
     * @return завершение проверки
     */
    private Mono<Void> checkSize(FilePart image) {
        Long declaredLength = image.headers().getContentLength();
        long limit = maxUploadSizeBytes();
        if (declaredLength != null && declaredLength > limit) {
            return Mono.error(tooLarge(limit));
        }
        return Mono.empty();
    }

    private ImageUploadException tooLarge(long limit) {
        return new ImageUploadException(
                "Изображение слишком большое. Максимальный размер — " + formatSize(limit));
    }

    private String formatSize(long bytes) {
        if (bytes % (1024L * 1024L * 1024L) == 0) {
            return (bytes / (1024L * 1024L * 1024L)) + " ГБ";
        }
        if (bytes % (1024L * 1024L) == 0) {
            return (bytes / (1024L * 1024L)) + " МБ";
        }
        if (bytes % 1024L == 0) {
            return (bytes / 1024L) + " КБ";
        }
        return bytes + " Б";
    }

    /**
     * Разбирает размер вида {@code 5MB} в байты.
     *
     * <p>Разбирается вручную, а не через {@code DataSize}, потому что
     * свойство приходит строкой из конфигурации, и молчаливая подстановка
     * нуля обрекла бы файл любого размера без предупреждения.
     *
     * <p>Неизвестный суффикс (например, {@code TB} или несуществующий)
     * выбрасывает {@link IllegalArgumentException}, чтобы не принять
     * конфигурацию с неверной единицей измерения.
     *
     * @param size размер с суффиксом единицы
     * @return размер в байтах
     * @throws IllegalArgumentException если формат размера некорректен
     */
    private long parseSize(String size) {
        if (size == null || size.isBlank()) {
            return 1024L * 1024L;
        }
        String trimmed = size.trim().toUpperCase(Locale.ROOT);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\d+)\\s*(KB|MB|GB)$").matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Неподдерживаемый формат размера: '" + size
                    + "'. Ожидается целое число с суффиксом (например, 5MB, 10KB, 2GB).");
        }
        long value = Long.parseLong(matcher.group(1));
        long multiplier =
                switch (matcher.group(2)) {
                    case "KB" -> 1024L;
                    case "MB" -> 1024L * 1024L;
                    case "GB" -> 1024L * 1024L * 1024L;
                    default -> throw new IllegalArgumentException(
                            "Неподдерживаемый суффикс размера: '" + size + "'. Ожидается KB, MB или GB.");
                };
        return value * multiplier;
    }

    /**
     * Читает содержимое части запроса целиком, но не более настроенного лимита.
     *
     * <p>Проверка идёт по фактически прочитанным байтам, поэтому срабатывает и при
     * отсутствии {@code Content-Length}, и при заниженном заголовке.
     *
     * @param image часть запроса с файлом
     * @return содержимое файла
     */
    Mono<byte[]> readBytes(FilePart image) {
        long limit = maxUploadSizeBytes();
        int maxByteCount = limit > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) limit;
        return DataBufferUtils.join(image.content(), maxByteCount)
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .onErrorResume(DataBufferLimitException.class, ex -> Mono.error(tooLarge(limit)))
                .onErrorResume(
                        IllegalArgumentException.class, ex -> Mono.error(new ImageUploadException(ex.getMessage())));
    }

    /**
     * Собирает запрос на запись объекта.
     *
     * @param key ключ объекта (расширение определяет тип содержимого)
     * @return запрос на запись
     */
    private PutObjectRequest putRequest(String key) {
        return PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(contentTypeOf(key).toString())
                .build();
    }
}
