package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.dto.CartPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CartServiceTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    void getCartReturnsItemsAndTotal() {
        Item item1 = itemRepository.save(
                new Item("Тестовая куртка", "Зимняя", "img1.jpg", new java.math.BigDecimal("1000")));
        Item item2 = itemRepository.save(
                new Item("Тестовый рюкзак", "Городской", "img2.jpg", new java.math.BigDecimal("2000")));
        cartItemRepository.save(new CartItem(item1, 2));
        cartItemRepository.save(new CartItem(item2, 1));

        CartPage cart = cartService.getCart();

        assertThat(cart.items()).hasSize(2);
        assertThat(cart.items())
                .extracting(ItemDto::title)
                .containsExactlyInAnyOrder("Тестовая куртка", "Тестовый рюкзак");
        assertThat(cart.total()).isEqualTo(1000L * 2 + 2000L * 1);
    }

    @Test
    void getCartEmptyReturnsZeroTotal() {
        CartPage cart = cartService.getCart();
        assertThat(cart.items()).isEmpty();
        assertThat(cart.total()).isEqualTo(0L);
    }

    @Test
    void applyActionPlusAddsNew() {
        Item item = itemRepository.save(
                new Item("Тестовый товар", "Описание", "img.jpg", new java.math.BigDecimal("1500")));

        cartService.applyAction(item.getId(), Action.PLUS);

        CartItem saved = cartItemRepository.findByItemId(item.getId()).orElseThrow();
        assertThat(saved.getCount()).isEqualTo(1);
    }

    @Test
    void applyActionPlusIncrementsExisting() {
        Item item = itemRepository.save(
                new Item("Тестовый товар", "Описание", "img.jpg", new java.math.BigDecimal("1500")));
        cartItemRepository.save(new CartItem(item, 2));

        cartService.applyAction(item.getId(), Action.PLUS);

        CartItem saved = cartItemRepository.findByItemId(item.getId()).orElseThrow();
        assertThat(saved.getCount()).isEqualTo(3);
    }

    @Test
    void applyActionMinusDecrements() {
        Item item = itemRepository.save(
                new Item("Тестовый товар", "Описание", "img.jpg", new java.math.BigDecimal("1500")));
        cartItemRepository.save(new CartItem(item, 3));

        cartService.applyAction(item.getId(), Action.MINUS);

        CartItem saved = cartItemRepository.findByItemId(item.getId()).orElseThrow();
        assertThat(saved.getCount()).isEqualTo(2);
    }

    @Test
    void applyActionMinusRemovesWhenZero() {
        Item item = itemRepository.save(
                new Item("Тестовый товар", "Описание", "img.jpg", new java.math.BigDecimal("1500")));
        cartItemRepository.save(new CartItem(item, 1));

        cartService.applyAction(item.getId(), Action.MINUS);

        assertThat(cartItemRepository.findByItemId(item.getId())).isEmpty();
    }

    @Test
    void applyActionDeleteRemoves() {
        Item item = itemRepository.save(
                new Item("Тестовый товар", "Описание", "img.jpg", new java.math.BigDecimal("1500")));
        cartItemRepository.save(new CartItem(item, 5));

        cartService.applyAction(item.getId(), Action.DELETE);

        assertThat(cartItemRepository.findByItemId(item.getId())).isEmpty();
    }

    @Test
    void applyActionDeleteNonExistentIsNoOp() {
        cartService.applyAction(999L, Action.DELETE);
        assertThat(cartItemRepository.findByItemId(999L)).isEmpty();
    }
}
