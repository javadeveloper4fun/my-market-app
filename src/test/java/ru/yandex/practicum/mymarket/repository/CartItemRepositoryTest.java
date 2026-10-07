package ru.yandex.practicum.mymarket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.mymarket.config.LiquibaseDataSourceConfig;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;

/**
 * Тесты репозитория корзины на встроенной H2 (профиль test).
 *
 * <p>Изоляция тестов и подключение миграций — по тем же причинам, что и в
 * {@link ItemRepositoryTest}; подробности там.
 *
 * <p>Метода {@code findAllWithItem}, который был в блокирующей версии, здесь
 * нет: он держался на {@code join fetch}, а R2DBC так не умеет. Позиция
 * корзины хранит плоский {@code itemId}, а товар добирается отдельным запросом.
 */
@DataR2dbcTest
@ImportAutoConfiguration(LiquibaseAutoConfiguration.class)
@Import(LiquibaseDataSourceConfig.class)
@ActiveProfiles("test")
class CartItemRepositoryTest extends DatabaseResetTest {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ItemRepository itemRepository;

    /**
     * Проверяет сохранение позиции корзины с количеством товара.
     */
    @Test
    void savesCartItemWithItemId() {
        Item item = save(item("Куртка", "4500.00"));

        CartItem cartItem =
                cartItemRepository.save(new CartItem(item.getId(), 2)).block();

        assertThat(cartItem).isNotNull();
        assertThat(cartItem.getId()).isNotNull();
        assertThat(cartItem.getItemId()).isEqualTo(item.getId());
        assertThat(cartItem.getCount()).isEqualTo(2);
    }

    /**
     * Проверяет выборку позиций корзины по переданным товарам.
     */
    @Test
    void findAllByItemIdInLoadsOnlyRequestedItems() {
        Item jacket = save(item("Куртка", "4500.00"));
        Item boots = save(item("Ботинки", "5200.00"));
        Item scarf = save(item("Шарф", "800.00"));
        saveCartItem(jacket, 1);
        saveCartItem(boots, 3);

        List<CartItem> found = cartItemRepository
                .findAllByItemIdIn(List.of(jacket.getId(), boots.getId()))
                .collectList()
                .block();

        assertThat(found).hasSize(2);
        assertThat(found.stream().map(CartItem::getItemId)).containsExactlyInAnyOrder(jacket.getId(), boots.getId());
        assertThat(found.stream().map(CartItem::getCount)).containsExactlyInAnyOrder(1, 3);
        assertThat(found.stream().map(CartItem::getItemId)).doesNotContain(scarf.getId());
    }

    /**
     * Проверяет поиск позиции корзины по идентификатору товара.
     */
    @Test
    void findByItemIdReturnsCartItem() {
        Item item = save(item("Куртка", "4500.00"));
        saveCartItem(item, 4);

        CartItem found = cartItemRepository.findByItemId(item.getId()).block();

        assertThat(found).isNotNull();
        assertThat(found.getItemId()).isEqualTo(item.getId());
        assertThat(found.getCount()).isEqualTo(4);
    }

    /**
     * Проверяет, что поиск отсутствующей позиции даёт пустой результат, а не
     * исключение: этим свойством пользуется сервис корзины, решая, создавать ли
     * позицию.
     */
    @Test
    void findByItemIdReturnsEmptyWhenAbsent() {
        Item item = save(item("Куртка", "4500.00"));

        assertThat(cartItemRepository.findByItemId(item.getId()).block()).isNull();
    }

    /**
     * Проверяет, что один и тот же товар не может попасть в корзину дважды
     * (уникальное ограничение на {@code item_id}).
     *
     * <p>Ожидаемое исключение — {@link DuplicateKeyException}: в блокирующей
     * версии JPA превращал нарушение ограничения в
     * {@code DataIntegrityViolationException}, а R2DBC сообщает о нарушении
     * уникального индекса именно так.
     */
    @Test
    void duplicateItemInCartFails() {
        Item item = save(item("Куртка", "4500.00"));
        saveCartItem(item, 1);

        assertThatThrownBy(() ->
                        cartItemRepository.save(new CartItem(item.getId(), 1)).block())
                .isInstanceOf(DuplicateKeyException.class);
    }

    /**
     * Создаёт и сохраняет товар.
     *
     * @param item товар для сохранения
     * @return сохранённый товар
     */
    private Item save(Item item) {
        return itemRepository.save(item).block();
    }

    /**
     * Создаёт и сохраняет позицию корзины.
     *
     * @param item  товар позиции
     * @param count количество
     * @return сохранённая позиция
     */
    private CartItem saveCartItem(Item item, int count) {
        return cartItemRepository.save(new CartItem(item.getId(), count)).block();
    }

    /**
     * Создаёт товар для теста.
     *
     * @param title название
     * @param price цена
     * @return новый товар
     */
    private Item item(String title, String price) {
        return new Item(title, "Описание " + title, "images/test.jpg", new BigDecimal(price));
    }
}
