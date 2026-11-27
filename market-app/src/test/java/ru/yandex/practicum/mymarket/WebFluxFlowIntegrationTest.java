package ru.yandex.practicum.mymarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.User;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.repository.UserRepository;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;
import ru.yandex.practicum.mymarket.support.StubPaymentsConfiguration;

@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(StubPaymentsConfiguration.class)
class WebFluxFlowIntegrationTest extends DatabaseResetTest {

    @Autowired
    private org.springframework.context.ApplicationContext context;

    private WebTestClient client;

    @org.junit.jupiter.api.BeforeEach
    void setUpClient() {
        client = WebTestClient.bindToApplicationContext(context)
                .apply(
                        org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                                .springSecurity())
                .configureClient()
                .build();
    }

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.r2dbc.core.DatabaseClient databaseClient;

    @Autowired
    private StubPaymentsConfiguration.StubPaymentsGateway payments;

    private User testUser;

    @BeforeEach
    void resetPaymentsStub() {
        payments.setBalance(1_000_000L);
        payments.setUnavailable(false);
        testUser = userRepository.findByUsername("user1").block();
        if (testUser == null) {
            databaseClient
                    .sql("INSERT INTO users (username, password_hash, enabled, created_at) "
                            + "VALUES ('user1','{noop}password',true,CURRENT_TIMESTAMP)")
                    .fetch()
                    .rowsUpdated()
                    .block();
            testUser = userRepository.findByUsername("user1").block();
        }
    }

    @Test
    void startsWithEmptyDatabaseBecauseEachTestCleansUp() {
        assertThat(itemRepository.count().block()).isZero();
        assertThat(cartItemRepository.count().block()).isZero();
        assertThat(orderRepository.count().block()).isZero();
    }

    @Test
    void fullFlowThroughShowcaseCartAndOrder() {
        Item item = saveItem("Куртка", "Тёплая", "4500");

        client.get().uri("/items").exchange().expectStatus().isOk();

        authed().post()
                .uri("/items?id=" + item.getId() + "&action=PLUS&search=&sort=NO&pageNumber=1&pageSize=5")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .location("/items?search=&sort=NO&pageNumber=1&pageSize=5");

        authed().get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Куртка").contains("4500"));

        authed().post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .value("Location", l -> assertThat(l).contains("/orders/").endsWith("?newOrder=true"));

        authed().get()
                .uri("/orders")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Заказ"));
    }

    @Test
    void cartIsEmptyAfterBuying() {
        Item item = saveItem("Шапка", "Тёплая", "5200");

        authed().post()
                .uri("/cart/items?id=" + item.getId() + "&action=PLUS")
                .exchange()
                .expectStatus()
                .isOk();
        authed().post().uri("/buy").exchange().expectStatus().is3xxRedirection();

        authed().get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).doesNotContain("5200"));

        assertThat(cartItemRepository.count().block()).isZero();
    }

    @Test
    void buyRedirectsToCartWhenFundsAreInsufficient() {
        Item item = saveItem("Куртка", "Тёплая", "4500");
        cartItemRepository.save(new CartItem(item.getId(), 1, testUser.getId())).block();
        payments.setBalance(0);

        authed().post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .value(
                        "Location",
                        l -> assertThat(l).contains("/cart/items").contains("paymentError=insufficient-funds"));

        assertThat(orderRepository.count().block()).isEqualTo(1);
        assertThat(orderRepository.findAll().collectList().block().getFirst().getStatus())
                .isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(cartItemRepository.count().block()).isEqualTo(1);
    }

    @Test
    void buyRedirectsToCartWhenPaymentsAreUnavailable() {
        Item item = saveItem("Куртка", "Тёплая", "4500");
        cartItemRepository.save(new CartItem(item.getId(), 1, testUser.getId())).block();
        payments.setUnavailable(true);

        authed().post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .value(
                        "Location",
                        l -> assertThat(l)
                                .contains("/cart/items")
                                .contains("paymentError=payments-unavailable")
                                .contains("attemptId="));

        assertThat(orderRepository.count().block()).isEqualTo(1);
        assertThat(orderRepository.findAll().collectList().block().getFirst().getStatus())
                .isEqualTo("AWAITING_PAYMENT");
        assertThat(cartItemRepository.count().block()).isEqualTo(1);
    }

    @Test
    void cartShowsPaymentsUnavailableMessage() {
        Item item = saveItem("Куртка", "Тёплая", "4500");
        cartItemRepository.save(new CartItem(item.getId(), 1, testUser.getId())).block();
        payments.setUnavailable(true);

        authed().get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html ->
                        assertThat(html).contains("Сервис платежей недоступен").doesNotContain("Баланс:"));
    }

    @Test
    void getItemNotFoundReturns404() {
        client.get().uri("/items/999999").exchange().expectStatus().isNotFound();
    }

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

    @Test
    void getOrderNotFoundReturns404() {
        authed().get().uri("/orders/999999").exchange().expectStatus().isNotFound();
    }

    @Test
    void getItemWithInvalidIdReturns400() {
        client.get().uri("/items/abc").exchange().expectStatus().isBadRequest();
    }

    private WebTestClient authed() {
        return client.mutateWith(mockUser("user1").roles("USER")).mutateWith(csrf());
    }

    private Item saveItem(String title, String description, String price) {
        return itemRepository
                .save(new Item(title, description, "images/jacket.jpg", new BigDecimal(price)))
                .block();
    }
}
