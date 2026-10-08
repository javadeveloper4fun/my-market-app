package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.r2dbc.UncategorizedR2dbcException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;

/**
 * Проверки создания товара с картинкой.
 *
 * <p>Хранилище подменено заглушкой: проверяется порядок операций и компенсация,
 * а не сам разговор с S3 — для этого есть стенд в compose.
 *
 * <p>Неудача вставки вызывается по-настоящему, длинным названием: столбец
 * {@code title} объявлен как {@code VARCHAR(255)}, и превышение длины
 * отсекается базой. Подмена репозитория заглушкой дала бы тест, который
 * проверял бы только то, что замокал сам, а реальный отказ базы проходит
 * иначе, чем подставленный.
 */
@SpringBootTest
@ActiveProfiles("test")
class ItemCreationTest extends DatabaseResetTest {

    private static final String KEY = "5f0b1c2d-0000-4000-8000-000000000001";

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @MockitoBean
    private ImageStorageService imageStorageService;

    /**
     * Проверяет, что путь к картинке сохраняется в том же виде, что и у товаров
     * сид-миграции.
     *
     * <p>Формат {@code images/<ключ>} обязателен: шаблон строит адрес как
     * {@code "/" + imgPath}, и при другой записи картинки перестали бы
     * открываться. Проверка сравнивает с тем, что лежит в сиде, а не с
     * литералом в коде.
     */
    @Test
    void storesImagePathInSameFormatAsSeedItems() {
        when(imageStorageService.upload(any())).thenReturn(Mono.just(KEY));

        ItemDto created = itemService
                .createItem("Шапка", "Вязаная", 1200, image("hat.png"))
                .block();

        assertThat(created).isNotNull();
        assertThat(created.imgPath()).isEqualTo("images/" + KEY);
        Item saved = itemRepository.findById(created.id()).block();
        assertThat(saved).isNotNull();
        assertThat(saved.getImgPath()).isEqualTo("images/" + KEY);
    }

    /**
     * Проверяет, что созданный товар попадает в витрину с нулевым количеством
     * в корзине.
     *
     * <p>Количество ненулевое было бы признаком того, что товар куда-то
     * добавили, — на витрине такой товар сразу показался бы с кнопками
     * изменения количества, хотя его в корзине нет.
     */
    @Test
    void createdItemHasZeroCountInCart() {
        when(imageStorageService.upload(any())).thenReturn(Mono.just(KEY));

        ItemDto created = itemService
                .createItem("Шапка", "Вязаная", 1200, image("hat.png"))
                .block();

        assertThat(created).isNotNull();
        assertThat(created.count()).isZero();
    }

    /**
     * Проверяет, что цена сохраняется с точностью, заданной схемой.
     */
    @Test
    void storesPriceAsGiven() {
        when(imageStorageService.upload(any())).thenReturn(Mono.just(KEY));

        ItemDto created = itemService
                .createItem("Шапка", "Вязаная", 1200, image("hat.png"))
                .block();

        assertThat(created).isNotNull();
        assertThat(created.price()).isEqualTo(1200L);
        Item saved = itemRepository.findById(created.id()).block();
        assertThat(saved).isNotNull();
        assertThat(saved.getPrice()).isEqualByComparingTo("1200");
    }

    /**
     * Проверяет компенсирующее удаление: если товар не сохранился, положенная
     * картинка убирается из хранилища.
     *
     * <p>Хранилище не участвует в транзакции базы и откатиться вместе с ней не
     * может, поэтому порядок операций важнее, чем кажется. Без удаления в
     * хранилище копились бы картинки, на которые никто не ссылается.
     *
     * <p>Неудача вызывается по-настоящему: название длиннее, чем позволяет
     * колонка {@code VARCHAR(255)}, и база отклоняет его сама. Подставленная
     * ошибка репозитория прошла бы по другой ветке кода, и компенсация на
     * такой неудаче не проверялась бы вовсе.
     *
     * <p>Тип исключения указан явно, а не «лишь бы что-то бросилось»: иначе
     * тест прошёл бы и при ошибке, не связанной с базой, — например, при
     * неверном типе аргумента или при вторичной ошибке компенсации, и пропустил
     * бы дефект.
     *
     * <p>Указан {@link org.springframework.r2dbc.UncategorizedR2dbcException}, а не
     * {@code DataIntegrityViolationException}: в этой конфигурации ошибка от
     * драйвера не переводится в исключение слоя доступа к данным, и наружу
     * выходит именно этот тип. Если перевод когда-нибудь появится, тест
     * упадёт — и это правильно: он заставит задуматься, а не промолчит.
     */
    @Test
    void deletesUploadedImageWhenItemIsNotSaved() {
        when(imageStorageService.upload(any())).thenReturn(Mono.just(KEY));
        // Заглушка на удаление обязательна и неочевидна: у не-заглушенного мока
        // delete() возвращает null, и вызов .then(...) внутри компенсации падает
        // с NullPointerException. Наружу тогда выходит NPE вместо исходной
        // ошибки базы, и проверка проходит, не замечая, что ничего не проверила.
        when(imageStorageService.delete(KEY)).thenReturn(Mono.empty());
        String tooLongTitle = "я".repeat(300);

        assertThatThrownBy(() -> itemService
                        .createItem(tooLongTitle, "Описание", 500, image("long.jpg"))
                        .block())
                .isInstanceOf(UncategorizedR2dbcException.class);

        verify(imageStorageService).delete(KEY);
    }

    /**
     * Проверяет, что при успешном создании картинка остаётся в хранилище.
     *
     * <p>Обратная к предыдущей проверка: удалять объект нужно только при
     * неудаче, и лишний вызов удаления стёр бы картинку у только что созданного
     * товара — с витрины она стала бы битой, и заметить это можно было бы
     * только при открытии карточки.
     */
    @Test
    void keepsUploadedImageWhenItemIsSaved() {
        when(imageStorageService.upload(any())).thenReturn(Mono.just(KEY));

        itemService.createItem("Шапка", "Вязаная", 1200, image("hat.png")).block();

        verify(imageStorageService, never()).delete(any());
    }

    /**
     * Готовит часть запроса с картинкой заданного имени файла.
     *
     * <p>Реализация написана здесь, а не взята готовой, потому что готовая
     * реализация части в каркасе — сервлетная, {@code jakarta.servlet.http.Part},
     * и для WebFlux она не годится: имя файла нужно сервису для построения ключа,
     * а содержимое нужно отдать потоком.
     *
     * @param filename имя файла с расширением
     * @return часть запроса с файлом
     */
    private FilePart image(String filename) {
        byte[] content = {1, 2, 3};
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(content.length);
        return new FilePart() {

            @Override
            public String filename() {
                return filename;
            }

            @Override
            public Flux<DataBuffer> content() {
                return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(content));
            }

            @Override
            public HttpHeaders headers() {
                return headers;
            }

            @Override
            public String name() {
                return "image";
            }

            @Override
            public Mono<Void> transferTo(Path destination) {
                return Mono.empty();
            }
        };
    }
}
