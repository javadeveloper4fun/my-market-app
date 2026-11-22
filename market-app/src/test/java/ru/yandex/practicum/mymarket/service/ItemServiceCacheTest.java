package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.cache.CachedItem;
import ru.yandex.practicum.mymarket.cache.CachedItemsPage;
import ru.yandex.practicum.mymarket.cache.ItemCache;
import ru.yandex.practicum.mymarket.cache.ItemsPageKey;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

/**
 * Тесты cache-aside витрины на подменённом кеше.
 *
 * <p>Настоящий Redis проверяется отдельным {@code RedisItemCacheIntegrationTest};
 * здесь предметный интерес — порядок операций в {@link ItemService}: попадание,
 * промах с записью в кеш и инвалидация при создании товара.
 */
@ExtendWith(MockitoExtension.class)
class ItemServiceCacheTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ImageStorageService imageStorageService;

    @Mock
    private ItemCache itemCache;

    @Mock
    private TransactionalOperator transactionalOperator;

    private ItemService itemService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(itemCache.catalogVersion()).thenReturn(Mono.just(0L));
        itemService = new ItemService(
                itemRepository, cartItemRepository, imageStorageService, itemCache, transactionalOperator);
    }

    @Test
    void cardIsReadFromCacheAndCountFromDatabase() {
        when(itemCache.getItem(1L)).thenReturn(Mono.just(Optional.of(cachedItem())));
        when(cartItemRepository.findByItemIdAndUserId(1L, 1L)).thenReturn(Mono.empty());

        ItemDto dto = itemService.getItem(1L, 1L).block();

        assertThat(dto.title()).isEqualTo("Куртка");
        assertThat(dto.count()).isZero();
        verify(itemRepository, never()).findById(anyLong());
    }

    @Test
    void cardIsLoadedFromDatabaseOnCacheMissAndWrittenBack() {
        when(itemCache.getItem(1L)).thenReturn(Mono.just(Optional.empty()));
        when(itemRepository.findById(1L)).thenReturn(Mono.just(item()));
        when(itemCache.putItem(any())).thenReturn(Mono.empty());
        when(cartItemRepository.findByItemIdAndUserId(1L, 1L)).thenReturn(Mono.empty());

        ItemDto dto = itemService.getItem(1L, 1L).block();

        assertThat(dto.title()).isEqualTo("Куртка");
        verify(itemCache).putItem(any(CachedItem.class));
    }

    @Test
    void pageIsReadFromCacheWithoutDatabaseQueries() {
        when(itemCache.getPage(any()))
                .thenReturn(Mono.just(Optional.of(new CachedItemsPage(List.of(cachedItem()), 1L))));
        when(cartItemRepository.findAllByItemIdInAndUserId(any(), any())).thenReturn(Flux.empty());

        var page = itemService.getItems("", "NO", 1, 10, 1L).block();

        assertThat(page.rows().getFirst())
                .filteredOn(dto -> dto.id() != ItemDto.PLACEHOLDER_ID)
                .hasSize(1);
        verify(itemRepository, never()).findAllUnsorted(anyLong(), anyLong());
        verify(itemRepository, never()).count();
    }

    @Test
    void pageIsLoadedFromDatabaseOnCacheMissAndWrittenBack() {
        when(itemCache.getPage(any())).thenReturn(Mono.just(Optional.empty()));
        when(itemRepository.findAllUnsorted(10L, 0L)).thenReturn(Flux.just(item()));
        when(itemRepository.count()).thenReturn(Mono.just(1L));
        when(itemCache.putPage(any(), any())).thenReturn(Mono.empty());
        when(cartItemRepository.findAllByItemIdInAndUserId(any(), any())).thenReturn(Flux.empty());

        var page = itemService.getItems("", "NO", 1, 10, 1L).block();

        assertThat(page.rows().getFirst())
                .filteredOn(dto -> dto.id() != ItemDto.PLACEHOLDER_ID)
                .hasSize(1);
        verify(itemCache).putPage(any(), any(CachedItemsPage.class));
    }

    @Test
    void lowercaseSortUsesCanonicalSortingAndCacheKey() {
        when(itemCache.getPage(any())).thenReturn(Mono.just(Optional.empty()));
        when(itemRepository.findAllByTitle(10L, 0L)).thenReturn(Flux.just(item()));
        when(itemRepository.count()).thenReturn(Mono.just(1L));
        when(itemCache.putPage(any(), any())).thenReturn(Mono.empty());
        when(cartItemRepository.findAllByItemIdInAndUserId(any(), any())).thenReturn(Flux.empty());

        var page = itemService.getItems("", "alpha", 1, 10, 1L).block();

        assertThat(page.sort()).isEqualTo("ALPHA");
        ArgumentCaptor<ItemsPageKey> keyCaptor = ArgumentCaptor.forClass(ItemsPageKey.class);
        verify(itemCache).putPage(keyCaptor.capture(), any(CachedItemsPage.class));
        assertThat(keyCaptor.getValue().sort()).isEqualTo("ALPHA");
        verify(itemRepository).findAllByTitle(10L, 0L);
        verify(itemRepository, never()).findAllUnsorted(anyLong(), anyLong());
    }

    @Test
    void createItemInvalidatesPages() {
        when(itemRepository.save(any())).thenReturn(Mono.just(item()));
        when(itemCache.invalidatePages()).thenReturn(Mono.empty());

        itemService.saveWithImage("Куртка", "Описание", 1500, "key").block();

        verify(transactionalOperator).transactional(any(Mono.class));
        verify(itemCache).invalidatePages();
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
