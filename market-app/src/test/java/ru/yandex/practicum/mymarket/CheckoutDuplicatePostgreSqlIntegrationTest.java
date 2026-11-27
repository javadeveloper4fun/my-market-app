package ru.yandex.practicum.mymarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.CheckoutAttempt;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.User;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CheckoutAttemptRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.repository.UserRepository;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;
import ru.yandex.practicum.mymarket.support.StubPaymentsConfiguration;

/**
 * Проверка десяти параллельных повторов оформления на настоящем PostgreSQL.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("testcontainers")
@Import({StubPaymentsConfiguration.class, PostgreSqlTestContainerConfiguration.class})
class CheckoutDuplicatePostgreSqlIntegrationTest extends DatabaseResetTest {

    private static final int ATTEMPTS = 10;
    private static final long INITIAL_BALANCE = 1_000_000L;
    private static final long PRICE = 4_500L;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CheckoutAttemptRepository checkoutAttemptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private StubPaymentsConfiguration.StubPaymentsGateway payments;

    private WebTestClient client;
    private User user;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
        payments.setBalance(INITIAL_BALANCE);
        payments.setUnavailable(false);
        payments.setPaymentDelay(Duration.ZERO);
        databaseClient
                .sql("INSERT INTO users (username, password_hash, enabled, created_at) "
                        + "VALUES ('user1','{noop}password',true,CURRENT_TIMESTAMP)")
                .then()
                .block();
        user = userRepository.findByUsername("user1").block();
        Item item = itemRepository
                .save(new Item("Куртка", "Тёплая", "images/jacket.jpg", BigDecimal.valueOf(PRICE)))
                .block();
        cartItemRepository.save(new CartItem(item.getId(), 1, user.getId())).block();
    }

    @Override
    protected List<String> resetStatements() {
        return postgreSqlResetStatements();
    }

    @Test
    void tenRetriesWithSameAttemptIdCreateSingleOrderAndSinglePayment() throws Exception {
        payments.setUnavailable(true);
        String location = authed().post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .returnResult(String.class)
                .getResponseHeaders()
                .getLocation()
                .toString();
        String attemptId = location.substring(location.indexOf("attemptId=") + "attemptId=".length());
        assertThat(attemptId).isNotBlank();
        payments.setUnavailable(false);

        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(ATTEMPTS)) {
            List<Future<Void>> futures = new ArrayList<>(ATTEMPTS);
            for (int i = 0; i < ATTEMPTS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent start was not released");
                    }
                    authed().post()
                            .uri("/buy?attemptId=" + attemptId)
                            .exchange()
                            .expectStatus()
                            .is3xxRedirection();
                    return null;
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(orderRepository.count().block()).isEqualTo(1L);
        assertThat(checkoutAttemptRepository.count().block()).isEqualTo(1L);
        CheckoutAttempt attempt =
                checkoutAttemptRepository.findByAttemptId(attemptId).block();
        assertThat(attempt).isNotNull();
        assertThat(attempt.getStatus()).isEqualTo(CheckoutAttempt.Status.PAID.name());
        assertThat(orderRepository.findAll().collectList().block().getFirst().getStatus())
                .isEqualTo("PAID");
        assertThat(cartItemRepository.count().block()).isZero();
        assertThat(payments.getBalance(user.getId()).block()).isEqualTo(INITIAL_BALANCE - PRICE);
    }

    @Test
    void twoConcurrentInitialClicksWithoutAttemptIdCreateSingleOrderAndSinglePayment() throws Exception {
        payments.setPaymentDelay(Duration.ofMillis(500));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> buyWithoutAttemptId(ready, start));
            Future<String> second = executor.submit(() -> buyWithoutAttemptId(ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> locations = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(locations)
                    .filteredOn(location -> location.contains("/orders/"))
                    .hasSize(1);
            assertThat(locations)
                    .filteredOn(location -> location.contains("paymentError=attempt-conflict"))
                    .hasSize(1);
        }

        assertThat(orderRepository.count().block()).isEqualTo(1L);
        assertThat(checkoutAttemptRepository.count().block()).isEqualTo(1L);
        assertThat(orderRepository.findAll().collectList().block().getFirst().getStatus())
                .isEqualTo("PAID");
        assertThat(cartItemRepository.count().block()).isZero();
        assertThat(payments.getBalance(user.getId()).block()).isEqualTo(INITIAL_BALANCE - PRICE);
    }

    private String buyWithoutAttemptId(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent start was not released");
        }
        return authed().post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .returnResult(String.class)
                .getResponseHeaders()
                .getLocation()
                .toString();
    }

    private WebTestClient authed() {
        return client.mutateWith(mockUser("user1").roles("USER")).mutateWith(csrf());
    }
}
