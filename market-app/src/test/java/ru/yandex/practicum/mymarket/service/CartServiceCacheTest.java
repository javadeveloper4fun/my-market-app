package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.cache.CachedItem;
import ru.yandex.practicum.mymarket.cache.ItemCache;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

/**
 * Тесты чтения метаданных корзины через кеш.
 *
 * <p>Количество по-прежнему приходит из {@code cart_item}: в кеше лежит только
 * карточка товара, и проверка фиксирует это разделение.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceCacheTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemCache itemCache;

    @InjectMocks
    private CartService cartService;

    @Test
    void cartMetadataIsReadFromCache() {
        when(cartItemRepository.findAllSortedByUserId(1L)).thenReturn(Flux.just(new CartItem(1L, 2, 1L)));
        when(itemCache.getItem(1L)).thenReturn(Mono.just(Optional.of(cachedItem())));

        var cart = cartService.getCart(1L).block();

        assertThat(cart.items()).singleElement().satisfies(dto -> {
            assertThat(dto.title()).isEqualTo("Куртка");
            assertThat(dto.count()).isEqualTo(2);
        });
        assertThat(cart.total()).isEqualTo(3000L);
        verify(itemRepository, never()).findAllById(ArgumentMatchers.<Iterable<Long>>any());
    }

    @Test
    void missingMetadataIsLoadedFromDatabaseAndWrittenToCache() {
        when(cartItemRepository.findAllSortedByUserId(1L)).thenReturn(Flux.just(new CartItem(1L, 1, 1L)));
        when(itemCache.getItem(1L)).thenReturn(Mono.just(Optional.empty()));
        when(itemRepository.findAllById(ArgumentMatchers.<Iterable<Long>>any())).thenReturn(Flux.just(item()));
        when(itemCache.putItem(any())).thenReturn(Mono.empty());

        var cart = cartService.getCart(1L).block();

        assertThat(cart.items()).singleElement().extracting(dto -> dto.title()).isEqualTo("Куртка");
        verify(itemCache).putItem(any(CachedItem.class));
    }

    private CachedItem cachedItem() {
        return new CachedItem(1L, "Куртка", "Описание", "img.jpg", 1500L);
    }

    private Item item() {
        Item item = new Item("Куртка", "Описание", "img.jpg", new BigDecimal("1500"));
        item.setId(1L);
        return item;
    }
}
