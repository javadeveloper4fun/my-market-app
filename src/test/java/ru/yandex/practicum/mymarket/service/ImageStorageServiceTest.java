package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Тесты утилитных методов {@link ImageStorageService}.
 *
 * <p>Методы не зависят от состояния сервиса (S3-клиента или свойств),
 * поэтому используются {@code null} в конструкторе.
 */
class ImageStorageServiceTest {

    private final ImageStorageService service = new ImageStorageService(null, null);

    @Test
    void contentTypeOf_png() {
        assertThat(service.contentTypeOf("image.png")).isEqualTo(MediaType.IMAGE_PNG);
    }

    @Test
    void contentTypeOf_gif() {
        assertThat(service.contentTypeOf("image.gif")).isEqualTo(MediaType.IMAGE_GIF);
    }

    @Test
    void contentTypeOf_jpg() {
        assertThat(service.contentTypeOf("image.jpg")).isEqualTo(MediaType.IMAGE_JPEG);
    }

    @Test
    void contentTypeOf_jpeg() {
        assertThat(service.contentTypeOf("image.jpeg")).isEqualTo(MediaType.IMAGE_JPEG);
    }

    @Test
    void contentTypeOf_unknownExtension() {
        assertThat(service.contentTypeOf("image.xyz")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }
}
