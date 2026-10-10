package ru.yandex.practicum.mymarket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;

/**
 * Тесты репозитория корзины на встроенной H2 (профиль test).
 */
@DataJpaTest
@ActiveProfiles("test")
class CartItemRepositoryTest {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ItemRepository itemRepository;

    /**
     * Проверяет сохранение позиции корзины с количеством товара
     * и загрузку её вместе с товаром.
     */
    @Test
    void savesCartItemWithItem() {
        Item item = itemRepository.save(item("Куртка", "4500.00"));

        CartItem cartItem = cartItemRepository.saveAndFlush(new CartItem(item, 2));

        assertThat(cartItem.getId()).isNotNull();
        assertThat(cartItem.getItem()).isEqualTo(item);
        assertThat(cartItem.getCount()).isEqualTo(2);
    }

    /**
     * Проверяет загрузку всех позиций корзины с товарами (fetch join).
     */
    @Test
    void findAllWithItemLoadsItems() {
        cartItemRepository.saveAndFlush(new CartItem(itemRepository.save(item("Куртка", "4500.00")), 1));
        cartItemRepository.saveAndFlush(new CartItem(itemRepository.save(item("Ботинки", "5200.00")), 3));

        var cartItems = cartItemRepository.findAllWithItem();

        assertThat(cartItems).hasSize(2);
        assertThat(cartItems)
                .allSatisfy(cartItem -> assertThat(cartItem.getItem()).isNotNull());
        assertThat(cartItems.stream().map(CartItem::getCount)).containsExactlyInAnyOrder(1, 3);
    }

    /**
     * Проверяет поиск позиции корзины по идентификатору товара.
     */
    @Test
    void findByItemIdReturnsCartItem() {
        Item item = itemRepository.save(item("Куртка", "4500.00"));
        cartItemRepository.saveAndFlush(new CartItem(item, 4));

        var found = cartItemRepository.findByItemId(item.getId());

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getItem()).isEqualTo(item);
        assertThat(found.orElseThrow().getCount()).isEqualTo(4);
    }

    /**
     * Проверяет, что один и тот же товар не может попасть в корзину дважды
     * (уникальное ограничение на связь с товаром).
     */
    @Test
    void duplicateItemInCartFails() {
        Item item = itemRepository.save(item("Куртка", "4500.00"));
        cartItemRepository.saveAndFlush(new CartItem(item, 1));

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(new CartItem(item, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
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
