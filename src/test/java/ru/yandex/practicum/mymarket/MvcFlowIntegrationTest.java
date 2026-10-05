package ru.yandex.practicum.mymarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

/**
 * Интеграционные MockMvc-тесты: сквозной сценарий
 * «витрина → корзина → оформление заказа → список заказов»
 * на полном Spring-контексте со встроенной H2 (профиль test).
 *
 * <p>Класс помечен {@link Transactional}: {@code @SpringBootTest} сам по себе
 * не откатывает изменения, поэтому без этой аннотации данные, созданные
 * одним тестом, оставались бы в общей базе {@code mem:market} и влияли бы
 * на следующие тесты. С откатом каждый тест начинает с одинакового состояния,
 * а проверка {@code startsWithEmptyDatabaseBecauseEachTestRollsBack}
 * фиксирует это требование.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MvcFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Проверяет, что тесты изолированы друг от друга.
     *
     * <p>База {@code mem:market} общая для всех тестовых классов профиля
     * {@code test} и живёт до конца JVM, поэтому данные, оставленные одним
     * тестом, иначе влияли бы на остальные. Ожидание пустых таблиц в начале
     * теста означает, что предыдущий тест был откатан: проверка не зависит
     * от порядка выполнения, потому что при откате состояние одинаково
     * всегда.
     */
    @Test
    void startsWithEmptyDatabaseBecauseEachTestRollsBack() {
        assertThat(itemRepository.count()).isZero();
        assertThat(cartItemRepository.count()).isZero();
        assertThat(orderRepository.count()).isZero();
    }

    /**
     * Проверяет полный путь пользователя: добавление в корзину со страницы
     * витрины, отображение корзины, оформление заказа и переход к заказу.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void fullFlowThroughShowcaseCartAndOrder() throws Exception {
        Item item = itemRepository.save(new Item("Куртка", "Утеплённая", "images/jacket.jpg", new BigDecimal("4500")));
        long itemId = item.getId();

        mockMvc.perform(get("/items")).andExpect(status().isOk()).andExpect(view().name("items"));

        mockMvc.perform(post("/items").param("id", String.valueOf(itemId)).param("action", "PLUS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/items?search=&sort=NO&pageNumber=1&pageSize=5"));

        mockMvc.perform(get("/cart/items"))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(model().attributeExists("items", "total"))
                .andExpect(model().attribute("total", 4500L));

        mockMvc.perform(post("/buy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/orders/*?newOrder=true"));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(model().attributeExists("orders"));
    }

    /**
     * Проверяет, что после оформления заказа корзина пуста.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void cartIsEmptyAfterBuying() throws Exception {
        Item item = itemRepository.save(new Item("Ботинки", "Кожаные", "images/boots.jpg", new BigDecimal("5200")));
        long itemId = item.getId();

        mockMvc.perform(post("/items").param("id", String.valueOf(itemId)).param("action", "PLUS"));

        mockMvc.perform(post("/buy")).andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/cart/items")).andExpect(status().isOk()).andExpect(model().attribute("total", 0L));
    }

    /**
     * Проверяет, что несуществующий товар отдаёт 404 на полном стеке.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void getItemNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/items/999999")).andExpect(status().isNotFound());
    }

    /**
     * Проверяет, что несуществующий заказ отдаёт 404 на полном стеке.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void getOrderNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/orders/999999")).andExpect(status().isNotFound());
    }
}
