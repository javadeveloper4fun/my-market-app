package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.r2dbc.spi.ConnectionFactory;
import java.math.BigDecimal;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.scheduler.Schedulers;
import ru.yandex.practicum.mymarket.PostgreSqlTestContainerConfiguration;
import ru.yandex.practicum.mymarket.config.LiquibaseDataSourceConfig;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;
import ru.yandex.practicum.mymarket.support.StubPaymentsConfiguration;

@SpringBootTest
@ActiveProfiles({"test", "testcontainers"})
@Import({LiquibaseDataSourceConfig.class, PostgreSqlTestContainerConfiguration.class, StubPaymentsConfiguration.class})
class CartServicePostgreSqlTest extends DatabaseResetTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private CartService cartService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Override
    protected List<String> resetStatements() {
        return postgreSqlResetStatements();
    }

    @BeforeEach
    void seedUser() {
        databaseClient
                .sql("INSERT INTO users (id, username, password_hash, enabled, created_at) "
                        + "VALUES (1, 'test', '{noop}p', true, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (id) DO NOTHING")
                .then()
                .block();
    }

    @Test
    void runsOnPostgreSqlDialectBranch() {
        assertThat(connectionFactory.getMetadata().getName()).containsIgnoringCase("postgres");
    }

    @Test
    void concurrentPlusCreatesSingleRowWithTotalCountOnRealPostgreSql() throws Exception {
        Item item = saveItem("Куртка", "Тёплая куртка", "4500.00");
        int attempts = 10;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
            List<Future<Void>> futures = new ArrayList<>(attempts);
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent start was not released");
                    }
                    cartService
                            .applyAction(item.getId(), Action.PLUS, USER_ID)
                            .subscribeOn(Schedulers.boundedElastic())
                            .block();
                    return null;
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        List<CartItem> positions =
                cartItemRepository.findAllSortedByUserId(USER_ID).collectList().block();
        assertThat(positions).hasSize(1);
        assertThat(positions.getFirst().getItemId()).isEqualTo(item.getId());
        assertThat(positions.getFirst().getCount()).isEqualTo(attempts);
    }

    private Item saveItem(String title, String description, String price) {
        return itemRepository
                .save(new Item(title, description, "img.jpg", new BigDecimal(price)))
                .block();
    }
}
