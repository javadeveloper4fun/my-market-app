package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.mymarket.dto.CartPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;

/**
 * Тесты сервиса корзины на встроенной H2 (профиль test).
 *
 * <p>Изоляция тестов — через явную очистку базы, а не через
 * {@code @Transactional}: на реактивном стеке отката не происходит, и почему —
 * сказано в {@link DatabaseResetTest}.
 *
 * <p>Почти каждый вызов завершается на {@code block()}. Методы сервиса
 * возвращают {@code Mono}, а ленивый publisher без подписки не выполнит ничего:
 * без {@code block()} тест прошёл бы, не сделав ни одного запроса, и упал бы
 * на проверке результата — или, что хуже, прошёл бы с пустым значением.
 */
@SpringBootTest
@ActiveProfiles("test")
class CartServiceTest extends DatabaseResetTest {

    private static final Long USER_ID = 1L;

    @org.junit.jupiter.api.BeforeEach
    void seedUser() {
        databaseClient.sql("MERGE INTO users (id, username, password_hash, enabled, created_at) KEY(id) VALUES (1, 'test', '{noop}p', true, CURRENT_TIMESTAMP)").then().block();
    }

    @Autowired
    private CartService cartService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    void getCartReturnsItemsAndTotal() {
        Item item1 = saveItem("Тестовая куртка", "Зимняя", "1000");
        Item item2 = saveItem("Тестовый рюкзак", "Городской", "2000");
        cartItemRepository.save(new CartItem(item1.getId(), 2, USER_ID)).block();
        cartItemRepository.save(new CartItem(item2.getId(), 1, USER_ID)).block();

        CartPage cart = cartService.getCart(1L).block();

        assertThat(cart.items()).hasSize(2);
        assertThat(cart.items())
                .extracting(ItemDto::title)
                .containsExactlyInAnyOrder("Тестовая куртка", "Тестовый рюкзак");
        assertThat(cart.total()).isEqualTo(1000L * 2 + 2000L * 1);
    }

    @Test
    void getCartEmptyReturnsZeroTotal() {
        CartPage cart = cartService.getCart(1L).block();

        assertThat(cart.items()).isEmpty();
        assertThat(cart.total()).isEqualTo(0L);
    }

    @Test
    void applyActionPlusAddsNew() {
        Item item = saveItem("Тестовый товар", "Описание", "1500");

        cartService.applyAction(item.getId(), Action.PLUS, 1L).block();

        CartItem saved = cartItemRepository.findByItemIdAndUserId(item.getId(), 1L).block();
        assertThat(saved).isNotNull();
        assertThat(saved.getCount()).isEqualTo(1);
    }

    /**
     * Проверяет, что повторное увеличение количества не создаёт вторую позицию.
     *
     * <p>Проверка важна сама по себе, а не как защита от регрессии. Реализация
     * ищет позицию, применяет действие и при отсутствии позиции создаёт её —
     * через {@code switchIfEmpty}. Результат действия пустой, потому что это
     * {@code Mono<Void>}, и {@code switchIfEmpty} за такой цепочкой срабатывал
     * всегда: на второй запрос он вставлял строку повторно и упирался в
     * уникальный индекс по {@code item_id}.
     */
    @Test
    void applyActionPlusTwiceIncrementsInsteadOfInsertingSecondRow() {
        Item item = saveItem("Тестовый товар", "Описание", "1500");

        cartService.applyAction(item.getId(), Action.PLUS, 1L).block();
        cartService.applyAction(item.getId(), Action.PLUS, 1L).block();

        List<CartItem> all = cartItemRepository.findAll().collectList().block();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getCount()).isEqualTo(2);
    }

    @Test
    void applyActionPlusIncrementsExisting() {
        Item item = saveItem("Тестовый товар", "Описание", "1500");
        cartItemRepository.save(new CartItem(item.getId(), 2, 1L)).block();

        cartService.applyAction(item.getId(), Action.PLUS, 1L).block();

        CartItem saved = cartItemRepository.findByItemIdAndUserId(item.getId(), 1L).block();
        assertThat(saved).isNotNull();
        assertThat(saved.getCount()).isEqualTo(3);
    }

    @Test
    void applyActionMinusDecrements() {
        Item item = saveItem("Тестовый товар", "Описание", "1500");
        cartItemRepository.save(new CartItem(item.getId(), 3, USER_ID)).block();

        cartService.applyAction(item.getId(), Action.MINUS, 1L).block();

        CartItem saved = cartItemRepository.findByItemIdAndUserId(item.getId(), 1L).block();
        assertThat(saved).isNotNull();
        assertThat(saved.getCount()).isEqualTo(2);
    }

    @Test
    void applyActionMinusRemovesWhenZero() {
        Item item = saveItem("Тестовый товар", "Описание", "1500");
        cartItemRepository.save(new CartItem(item.getId(), 1, 1L)).block();

        cartService.applyAction(item.getId(), Action.MINUS, 1L).block();

        assertThat(cartItemRepository.findByItemIdAndUserId(item.getId(), 1L).block()).isNull();
    }

    @Test
    void applyActionDeleteRemoves() {
        Item item = saveItem("Тестовый товар", "Описание", "1500");
        cartItemRepository.save(new CartItem(item.getId(), 5, USER_ID)).block();

        cartService.applyAction(item.getId(), Action.DELETE, 1L).block();

        assertThat(cartItemRepository.findByItemIdAndUserId(item.getId(), 1L).block()).isNull();
    }

    @Test
    void applyActionDeleteNonExistentIsNoOp() {
        cartService.applyAction(999L, Action.DELETE, 1L).block();

        assertThat(cartItemRepository.findByItemIdAndUserId(999L, 1L).block()).isNull();
    }

    /**
     * Проверяет, что увеличение отсутствующего товара в корзине не проходит
     * молча: позиция ссылается на каталог, и товара с таким идентификатором нет.
     */
    @Test
    void applyActionPlusOnMissingItemFails() {
        assertThatThrownBy(() -> cartService.applyAction(999L, Action.PLUS, 1L).block())
                .isInstanceOf(ItemNotFoundException.class);
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
                .save(new Item(title, description, "img.jpg", new BigDecimal(price)))
                .block();
    }
}
