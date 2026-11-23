package ru.yandex.practicum.mymarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.Order;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.repository.UserRepository;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;
import ru.yandex.practicum.mymarket.support.StubPaymentsConfiguration;

@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(StubPaymentsConfiguration.class)
class SecurityIntegrationTest extends DatabaseResetTest {

    @Autowired
    private ApplicationContext context;

    private WebTestClient client;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
        seedUser("user1");
        seedUser("user2");
    }

    @Test
    void anonymousCanAccessPublicPages() {
        client.get().uri("/items").exchange().expectStatus().isOk();
        client.get().uri("/login").exchange().expectStatus().isOk();
    }

    @Test
    void anonymousIsRedirectedFromProtectedPages() {
        client.get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .value("Location", l -> assertThat(l).contains("/login"));
        client.get()
                .uri("/orders")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .value("Location", l -> assertThat(l).contains("/login"));
    }

    @Test
    void anonymousCannotBuy() {
        client.mutateWith(csrf())
                .post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .value("Location", l -> assertThat(l).contains("/login"));
    }

    @Test
    void anonymousCannotMutateItems() {
        client.mutateWith(csrf())
                .post()
                .uri("/items?id=1&action=PLUS&search=&sort=NO&pageNumber=1&pageSize=5")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .value("Location", l -> assertThat(l).contains("/login"));

        client.mutateWith(csrf())
                .post()
                .uri("/items/1?action=PLUS")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .value("Location", l -> assertThat(l).contains("/login"));
    }

    @Test
    void postWithoutCsrfIsForbiddenForAuthenticatedUser() {
        Item item = itemRepository
                .save(new Item("Куртка", "d", "img.jpg", new BigDecimal("100")))
                .block();
        client.mutateWith(mockUser("user1").roles("USER"))
                .post()
                .uri("/items?id=" + item.getId() + "&action=PLUS&search=&sort=NO&pageNumber=1&pageSize=5")
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void authenticatedUserCanAccessCartAndOrders() {
        client.mutateWith(mockUser("user1").roles("USER"))
                .get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .isOk();
        client.mutateWith(mockUser("user1").roles("USER"))
                .get()
                .uri("/orders")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void cartIsIsolatedPerUser() {
        Item item = itemRepository
                .save(new Item("Куртка", "d", "img.jpg", new BigDecimal("100")))
                .block();
        Long user1 = userRepository.findByUsername("user1").block().getId();
        Long user2 = userRepository.findByUsername("user2").block().getId();
        cartItemRepository.save(new CartItem(item.getId(), 2, user1)).block();

        client.mutateWith(mockUser("user2").roles("USER"))
                .get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).doesNotContain("Куртка"));

        client.mutateWith(mockUser("user1").roles("USER"))
                .get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Куртка"));

        assertThat(cartItemRepository.findByItemIdAndUserId(item.getId(), user2).block())
                .isNull();
    }

    @Test
    void ordersAreIsolatedPerUser() {
        Long user1 = userRepository.findByUsername("user1").block().getId();
        Long user2 = userRepository.findByUsername("user2").block().getId();
        Order order = orderRepository.save(new Order(user1)).block();

        client.mutateWith(mockUser("user2").roles("USER"))
                .get()
                .uri("/orders/" + order.getId())
                .exchange()
                .expectStatus()
                .isNotFound();

        client.mutateWith(mockUser("user1").roles("USER"))
                .get()
                .uri("/orders/" + order.getId())
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void loginPageContainsForm() {
        client.get()
                .uri("/login")
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("name=\"username\"").contains("name=\"password\""));
    }

    private void seedUser(String username) {
        Long count = databaseClient
                .sql("SELECT COUNT(*) FROM users WHERE username=:u")
                .bind("u", username)
                .map(r -> r.get(0, Long.class))
                .one()
                .block();
        if (count != null && count > 0) {
            return;
        }
        databaseClient
                .sql("INSERT INTO users (username, password_hash, enabled, created_at) "
                        + "VALUES (:u,'{noop}password',true,CURRENT_TIMESTAMP)")
                .bind("u", username)
                .fetch()
                .rowsUpdated()
                .block();
        Long id = databaseClient
                .sql("SELECT id FROM users WHERE username=:u")
                .bind("u", username)
                .map(r -> r.get(0, Long.class))
                .one()
                .block();
        databaseClient
                .sql("INSERT INTO user_roles (user_id, role) VALUES (:id,'ROLE_USER')")
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .block();
    }
}
