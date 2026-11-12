package ru.yandex.practicum.mymarket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.ImageNotFoundException;
import ru.yandex.practicum.mymarket.service.ImageStorageService;
import ru.yandex.practicum.mymarket.service.ImageUploadException;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;

/**
 * Тесты контроллера картинок на {@link WebTestClient}.
 *
 * <p>Тест работает на полном контексте, а не в срезе {@code @WebFluxTest}. Срез не
 * поднимает инфраструктуру разбора multipart, и запрос с формой отвечал бы 415
 * независимо от правильности контроллера — ошибка проверки, а не кода.
 *
 * <p>Хранилище подменено заглушкой: его поведение проверяется отдельно, а здесь
 * важны маршрут, разбор пути ключа и ответы. Заглушка дополнительно проверяется
 * на вызов с ожидаемым ключом — именно здесь снимается префикс {@code images/}.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class ImagesControllerTest extends DatabaseResetTest {

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private ImageStorageService imageStorageService;

    /**
     * Проверяет, что загруженная картинка возвращается путём, который можно
     * положить в {@code img_path} и подставить в шаблон без правок.
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
     * Проверяет, что ключ доходит до хранилища ровно таким, каким пришёл в адресе.
     *
     * <p>В {@code img_path} лежит {@code images/<ключ>}, а шаблон строит адрес как
     * {@code "/" + imgPath}, и потому маршрут {@code /images/<ключ>} получает уже
     * голый ключ. Никакого дополнительного префикса снимать не нужно, а лишняя
     * обработка была бы способом испортить путь: молчаливого признака у такой
     * ошибки нет — картинки просто перестали бы отдаваться.
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
     * Проверяет, что JPEG-картинка отдаётся с {@code image/jpeg}.
     */
    @Test
    void servingImageUsesContentTypeByExtensionForJpeg() {
        when(imageStorageService.read("photo.jpg")).thenReturn(Mono.just(onePixelImage()));
        when(imageStorageService.contentTypeOf("photo.jpg")).thenReturn(MediaType.IMAGE_JPEG);

        client.get()
                .uri("/images/photo.jpg")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.IMAGE_JPEG);
    }

    /**
     * Проверяет, что ключ с вложенным каталогом доходит до хранилища целиком.
     *
     * <p>Маршрут жадный, и это сделано именно ради таких ключей: обычный шаблон
     * поймал бы только первый сегмент и молча отдал бы не ту картинку.
     */
    @Test
    void servingImagePassesNestedKeyWhole() {
        when(imageStorageService.read("catalog/2026/logo.png")).thenReturn(Mono.just(onePixelImage()));
        when(imageStorageService.contentTypeOf("catalog/2026/logo.png")).thenReturn(MediaType.IMAGE_PNG);

        client.get()
                .uri("/images/catalog/2026/logo.png")
                .exchange()
                .expectStatus()
                .isOk();

        verify(imageStorageService).read("catalog/2026/logo.png");
    }

    /**
     * Проверяет, что на странице добавления товара есть переходы в корзину и заказы.
     *
     * <p>Требование задания требует этих кнопок в правом верхнем углу любой
     * страницы, а не только основных модулей. Форму добавления это тоже касается:
     * без неё кнопки на ней просто забываются, и страница остаётся без
     * навигации. Проверка сделана по содержимому ответа, потому что кнопки
     * добавляются вручную в каждом шаблоне и тест ловит расхождение сразу.
     */
    @Test
    void newItemPageKeepsCartAndOrdersNavigation() {
        client.get()
                .uri("/items/new")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("href=\"/cart/items\"").contains("href=\"/orders\""));
    }

    /**
     * Проверяет, что недопустимый формат картинки даёт 400 с понятным текстом.
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
     *
     * <p>Ошибка «объекта нет» отдаётся как 404, и это видно пользователю: 500
     * выглядел бы как поломка приложения, хотя не работает только одна картинка.
     * Отдельно проверяется, что сбой хранилища под 404 не попадает — иначе
     * настоящие инциденты тихо превращались бы в «нет картинки».
     */
    @Test
    void servingMissingImageReturnsNotFound() {
        when(imageStorageService.read("absent.jpg")).thenReturn(Mono.error(new ImageNotFoundException("absent.jpg")));

        client.get().uri("/images/absent.jpg").exchange().expectStatus().isNotFound();
    }

    /**
     * Проверяет, что сбой хранилища не выдаётся за отсутствие картинки.
     */
    @Test
    void servingImageFailsWithServerErrorWhenStorageIsUnavailable() {
        when(imageStorageService.read("jacket.jpg"))
                .thenReturn(Mono.error(new IllegalStateException("Хранилище недоступно")));

        client.get().uri("/images/jacket.jpg").exchange().expectStatus().is5xxServerError();
    }

    /**
     * Собираёт multipart-форму с одним файлом.
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
