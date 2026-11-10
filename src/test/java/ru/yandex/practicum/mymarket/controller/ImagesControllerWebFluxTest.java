package ru.yandex.practicum.mymarket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.ImageNotFoundException;
import ru.yandex.practicum.mymarket.service.ImageStorageService;
import ru.yandex.practicum.mymarket.service.ImageUploadException;

/**
 * Тесты контроллера картинок в срезе {@code @WebFluxTest}.
 *
 * <p>Поднимается только веб-слой: контроллер и его мок-зависимости, без полного
 * контекста приложения (БД, S3, безопасность). Поэтому контекст поднимается
 * быстрее и занимает меньше памяти, чем {@link ImagesControllerTest}.
 *
 * <p>Multipart-кодек настраивается вручную через {@code WebFluxConfigurer}:
 * {@code @WebFluxTest} не поднимает его автоматически, и иначе запрос с
 * {@code multipart/form-data} на {@code POST /images} вернул бы 415 независимо
 * от правильности контроллера.
 */
@WebFluxTest(controllers = ImagesController.class)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class ImagesControllerWebFluxTest {

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private ImageStorageService imageStorageService;

    /**
     * Проверяет, что загруженная картинка возвращает путь, годный для
     * {@code img_path} товара.
     */
    @Test
    void uploadReturnsPathUsableAsImgPath() {
        when(imageStorageService.upload(any())).thenReturn(Mono.just("d1f0c4a2.jpg"));

        client.post()
                .uri("/images")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(formWith("photo.jpg", "image/jpeg")))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .isEqualTo("images/d1f0c4a2.jpg");
    }

    /**
     * Проверяет, что ключ доходит до хранилища без изменений.
     */
    @Test
    void servingImagePassesKeyUnchanged() {
        when(imageStorageService.read("jacket.jpg")).thenReturn(Mono.just(onePixelImage()));
        when(imageStorageService.contentTypeOf("jacket.jpg")).thenReturn(MediaType.IMAGE_JPEG);

        client.get()
                .uri("/images/jacket.jpg")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.IMAGE_JPEG);

        verify(imageStorageService).read("jacket.jpg");
    }

    /**
     * Проверяет, что тип ответа выводится из расширения ключа.
     */
    @Test
    void servingImageUsesContentTypeByExtension() {
        when(imageStorageService.read("logo.png")).thenReturn(Mono.just(onePixelImage()));
        when(imageStorageService.contentTypeOf("logo.png")).thenReturn(MediaType.IMAGE_PNG);

        client.get()
                .uri("/images/logo.png")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.IMAGE_PNG);
    }

    /**
     * Проверяет, что недопустимый формат картинки даёт 400 с текстом причины.
     */
    @Test
    void uploadRejectsUnsupportedFormat() {
        when(imageStorageService.upload(any()))
                .thenReturn(Mono.error(new ImageUploadException("Недопустимый формат изображения.")));

        client.post()
                .uri("/images")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(formWith("doc.pdf", "application/pdf")))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .isEqualTo("Недопустимый формат изображения.");
    }

    /**
     * Проверяет, что отсутствующая картинка даёт 404, а не 500.
     */
    @Test
    void servingMissingImageReturnsNotFound() {
        when(imageStorageService.read("absent.jpg")).thenReturn(Mono.error(new ImageNotFoundException("absent.jpg")));

        client.get().uri("/images/absent.jpg").exchange().expectStatus().isNotFound();
    }

    /**
     * Собирает multipart-форму с одним файлом.
     *
     * @param filename  имя файла
     * @param mediaType тип содержимого файла
     * @return тело запроса
     */
    private MultiValueMap<String, HttpEntity<?>> formWith(String filename, String mediaType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("image", new byte[] {1, 2, 3}).filename(filename).contentType(MediaType.parseMediaType(mediaType));
        return builder.build();
    }

    /**
     * Готовит поток из одного буфера — подставной «пиксель» для проверки отдачи.
     *
     * @return поток с содержимым картинки
     */
    private Flux<DataBuffer> onePixelImage() {
        ByteBuffer content = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9});
        return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(content));
    }
}
