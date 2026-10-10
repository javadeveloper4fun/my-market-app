package ru.yandex.practicum.mymarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;

/**
 * Интеграционные тесты сквозного сценария
 * «витрина → корзина → оформление заказа → список заказов»
 * на полном Spring-контексте со встроенной H2 (профиль test).
 *
 * <p>Запросы идут через {@link WebTestClient}, а не через MockMvc: приложение
 * работает на WebFlux, и проверять его нужно тем же способом, каким к нему
 * обращается браузер. Побочный эффект проверки такой же, как был у MockMvc, —
 * тесты видят настоящие шаблоны, настоящие обработчики исключений и настоящую
 * кодировку адресов.
 *
 * <p>Тема 10/11 «Конфигурирование и тестирование WebFlux», урок 3/4:
 * {@code WebTestClient} — аналог MockMvc для реактивного стека, и обе роли
 * он занимает сразу: проверяет аннотированные контроллеры и функциональные
 * эндпоинты без поднятия реального сервера. Создавать его вручную не нужно:
 * {@code @AutoConfigureWebTestClient} поднимает клиент, связанный с
 * контекстом теста. Отличать его от {@code WebClient} стоит: тот ходит
 * по сети и проверок ответа не умеет, а здесь доступны
 * {@code expectStatus()} и {@code expectBody()}, на которых построены
 * все проверки ниже.
 *
 * <p>Изоляция тестов — через явную очистку базы, а не через
 * {@code @Transactional}: откат на реактивном стеке не работает, и почему —
 * сказано в {@link DatabaseResetTest}. Это тем более важно здесь, чем в тестах
 * срезов: запросы обрабатываются на потоках Netty, а не на потоке теста, и
 * общая транзакция не связала бы их в любом случае.
 *
 * <p>POST-запросы несут параметры в query-строке, а не в теле: в WebFlux
 * {@code @RequestParam} читает только query-строку, тогда как в MVC он читал ещё
 * и тело формы.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class MvcFlowIntegrationTest extends DatabaseResetTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    /**
     * Проверяет, что тесты изолированы друг от друга.
     *
     * <p>База {@code mem:market} общая для всех тестовых классов профиля
     * {@code test} и живёт до конца JVM, поэтому данные, оставленные одним
     * тестом, иначе влияли бы на остальные. Проверка не зависит от порядка
     * выполнения, потому что очистка приводит состояние к одному виду всегда.
     */
    @Test
    void startsWithEmptyDatabaseBecauseEachTestCleansUp() {
        assertThat(itemRepository.count().block()).isZero();
        assertThat(cartItemRepository.count().block()).isZero();
        assertThat(orderRepository.count().block()).isZero();
    }

    /**
     * Проверяет полный путь пользователя: добавление в корзину со страницы
     * витрины, отображение корзины, оформление заказа и переход к заказу.
     */
    @Test
    void fullFlowThroughShowcaseCartAndOrder() {
        Item item = saveItem("Куртка", "Утеплённая", "4500");

        client.get().uri("/items").exchange().expectStatus().isOk();

        client.post()
                .uri("/items?id=" + item.getId() + "&action=PLUS&search=&sort=NO&pageNumber=1&pageSize=5")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .location("/items?search=&sort=NO&pageNumber=1&pageSize=5");

        client.get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Куртка").contains("4500"));

        client.post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .value(
                        "Location",
                        location -> assertThat(location).contains("/orders/").endsWith("?newOrder=true"));

        client.get()
                .uri("/orders")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Заказ"));
    }

    /**
     * Проверяет, что после оформления заказа корзина пуста.
     */
    @Test
    void cartIsEmptyAfterBuying() {
        Item item = saveItem("Ботинки", "Кожаные", "5200");

        client.post()
                .uri("/cart/items?id=" + item.getId() + "&action=PLUS")
                .exchange()
                .expectStatus()
                .isOk();
        client.post().uri("/buy").exchange().expectStatus().is3xxRedirection();

        client.get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).doesNotContain("5200"));

        assertThat(cartItemRepository.count().block()).isZero();
    }

    /**
     * Проверяет, что позиция заказа получает идентификатор заказа.
     *
     * <p>Связь держится на дисциплине вызывающего кода: R2DBC не сохраняет
     * вложенные сущности, поэтому {@code order_id} проставляется вручную. Если
     * забыть его, позиция сохранится, но окажется ни в одном заказе, и страница
     * заказа окажется пустой при непустой корзине на момент покупки.
     */
    @Test
    void orderItemsGetOrderIdSet() {
        Item item = saveItem("Куртка", "Утеплённая", "4500");
        cartItemRepository.save(new CartItem(item.getId(), 2)).block();

        String location = client.post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getFirst("Location");

        // Идентификатор берётся из адреса редиректа, а не вычисляется как count()+1.
        // Расчёт от количества строк опирался бы на допущение, что следующий
        // идентификатор равен текущему числу заказов плюс один, — оно верно только
        // пока идентификаторы выдаёт счётчик, сбрасываемый очисткой перед тестом.
        long orderId = Long.parseLong(location.substring("/orders/".length(), location.indexOf('?')));

        assertThat(orderItemRepository
                        .findAllByOrderIdIn(java.util.List.of(orderId))
                        .collectList()
                        .block())
                .hasSize(1)
                .allSatisfy(orderItem -> assertThat(orderItem.getOrderId()).isEqualTo(orderId));
    }

    /**
     * Проверяет, что несуществующий товар отдаёт 404 на полном стеке.
     */
    @Test
    void getItemNotFoundReturns404() {
        client.get().uri("/items/999999").exchange().expectStatus().isNotFound();
    }

    /**
     * Проверяет, что 404 показывается страницей, а не пустым ответом.
     *
     * <p>Проверка на статус тут недостаточна: пустой ответ со статусом 404 технически
     * правилен, но посетитель видит белую страницу без объяснения и без возможности
     * вернуться. Поэтому проверяется и тело: в нём должен быть адрес витрины,
     * иначе отказ снова станет немым.
     *
     * <p>Заголовок {@code Accept} задан явно: обработка ошибок выбирает формат ответа
     * по нему, и без {@code text/html} на эти запросы пришёл бы JSON. В браузере
     * заголовок именно такой, поэтому проверять надо именно его — иначе тест
     * проверял бы не тот сценарий, который увидит посетитель.
     */
    @Test
    void notFoundShowsErrorPageWithWayBack() {
        client.get()
                .uri("/items/999999")
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("href=\"/items\""));
    }

    /**
     * Проверяет, что несуществующий заказ отдаёт 404 на полном стеке.
     */
    @Test
    void getOrderNotFoundReturns404() {
        client.get().uri("/orders/999999").exchange().expectStatus().isNotFound();
    }

    /**
     * Проверяет, что некорректный идентификатор отдаёт 400, а не 404 или 500.
     */
    @Test
    void getItemWithInvalidIdReturns400() {
        client.get().uri("/items/abc").exchange().expectStatus().isBadRequest();
    }

    /**
     * Создаёт и сохраняет товар.
     *
     * @param title       название
     * @param description описание
     * @param price       цена
     * @return сохранённый товар
     */
    private Item saveItem(String title, String description, String price) {
        return itemRepository
                .save(new Item(title, description, "images/jacket.jpg", new BigDecimal(price)))
                .block();
    }
}
