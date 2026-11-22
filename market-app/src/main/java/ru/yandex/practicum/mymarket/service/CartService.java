package ru.yandex.practicum.mymarket.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.cache.CachedItem;
import ru.yandex.practicum.mymarket.cache.ItemCache;
import ru.yandex.practicum.mymarket.dto.CartPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

/**
 * Корзина покупателя.
 *
 * <p>Все операции выполняются в контексте конкретного пользователя ({@code userId}).
 * Позиции и товары хранятся в разных таблицах, и в блокирующей версии они
 * читались одним {@code join fetch}. На реактивном стеке это два запроса: сначала
 * позиции, затем товары по найденным идентификаторам. Порядок позиций
 * корзины сохраняется тем, что итоговый список собирается перебором позиций, а
 * не товаров: иначе выдача шла бы в порядке таблицы {@code items} и корзина
 * «переставлялась» сама.
 */
@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ItemRepository itemRepository;
    private final ItemCache itemCache;

    public CartService(CartItemRepository cartItemRepository, ItemRepository itemRepository, ItemCache itemCache) {
        this.cartItemRepository = cartItemRepository;
        this.itemRepository = itemRepository;
        this.itemCache = itemCache;
    }

    /**
     * Возвращает корзину пользователя.
     *
     * @param userId идентификатор пользователя
     * @return корзина со списком позиций и суммой
     */
    public Mono<CartPage> getCart(Long userId) {
        return cartItemRepository.findAllSortedByUserId(userId).collectList().flatMap(cartItems -> {
            if (cartItems.isEmpty()) {
                return Mono.just(new CartPage(List.of(), 0L));
            }
            List<Long> itemIds = cartItems.stream().map(CartItem::getItemId).toList();
            return itemsByIds(itemIds).map(items -> toPage(cartItems, items));
        });
    }

    /**
     * Читает метаданные товаров корзины из кеша, а недостающие докручивает из базы.
     *
     * <p>Количество в корзине здесь не участвует: оно меняется чаще каталога и
     * всегда читается из {@code cart_item}, поэтому в кеше лежит только карточка
     * товара без {@code count}.
     *
     * @param itemIds идентификаторы товаров в корзине
     * @return метаданные товаров по идентификаторам
     */
    private Mono<Map<Long, CachedItem>> itemsByIds(List<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        return Flux.fromIterable(itemIds)
                .concatMap(id ->
                        itemCache.getItem(id).defaultIfEmpty(Optional.empty()).map(hit -> Map.entry(id, hit)))
                .collectList()
                .flatMap(entries -> {
                    Map<Long, CachedItem> hits = entries.stream()
                            .filter(entry -> entry.getValue().isPresent())
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey, entry -> entry.getValue().get(), (a, b) -> a));
                    List<Long> missing = entries.stream()
                            .filter(entry -> entry.getValue().isEmpty())
                            .map(Map.Entry::getKey)
                            .distinct()
                            .toList();
                    if (missing.isEmpty()) {
                        return Mono.just(Map.copyOf(hits));
                    }
                    return itemRepository.findAllById(missing).collectList().flatMap(items -> {
                        Map<Long, CachedItem> merged = new HashMap<>(hits);
                        List<CachedItem> loaded =
                                items.stream().map(CachedItem::fromEntity).toList();
                        loaded.forEach(item -> merged.put(item.id(), item));
                        return Flux.fromIterable(loaded)
                                .concatMap(itemCache::putItem)
                                .then(Mono.just(Map.copyOf(merged)));
                    });
                });
    }

    /**
     * Применяет действие (увеличить/уменьшить/удалить) к позиции товара в корзине пользователя.
     *
     * <p>Изменения выполняются атомарными SQL-операциями с фильтром по user_id,
     * чтобы параллельные запросы не теряли обновления количества и пользователь
     * не мог менять чужую корзину. Для добавления новой позиции при гонке
     * используется уникальность {@code (item_id, user_id)}: при конфликте
     * вставки количество увеличивается повторно.
     *
     * @param itemId идентификатор товара
     * @param action действие над позицией
     * @param userId идентификатор пользователя
     * @return завершение операции
     */
    @Transactional
    public Mono<Void> applyAction(long itemId, Action action, Long userId) {
        return switch (action) {
            case PLUS -> increase(itemId, userId);
            case MINUS -> decrease(itemId, userId);
            case DELETE -> cartItemRepository.deleteByItemId(itemId, userId).then();
        };
    }

    private Mono<Void> increase(long itemId, Long userId) {
        return cartItemRepository.increaseCount(itemId, userId).flatMap(updated -> {
            if (updated > 0) {
                return Mono.empty();
            }
            return itemRepository
                    .findById(itemId)
                    .switchIfEmpty(Mono.error(() -> new ItemNotFoundException(itemId)))
                    .then(cartItemRepository
                            .save(new CartItem(itemId, 1, userId))
                            .then())
                    .onErrorResume(
                            DuplicateKeyException.class,
                            e -> cartItemRepository
                                    .increaseCount(itemId, userId)
                                    .then());
        });
    }

    private Mono<Void> decrease(long itemId, Long userId) {
        return cartItemRepository
                .decreaseCount(itemId, 1, userId)
                .then(cartItemRepository.deleteIfEmpty(itemId, userId))
                .then();
    }

    /**
     * Собирает страницу корзины, сохраняя порядок позиций.
     *
     * @param cartItems позиции корзины в порядке выдачи
     * @param items     товары по идентификаторам позиций
     * @return корзина со списком позиций и суммой
     */
    private CartPage toPage(List<CartItem> cartItems, Map<Long, CachedItem> items) {
        List<ItemDto> dtos = cartItems.stream()
                .filter(cartItem -> items.containsKey(cartItem.getItemId()))
                .map(cartItem -> items.get(cartItem.getItemId()).toDto(cartItem.getCount()))
                .toList();
        long total = dtos.stream().mapToLong(dto -> dto.price() * dto.count()).sum();
        return new CartPage(dtos, total);
    }
}
