package ru.yandex.practicum.mymarket.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

/**
 * Витрина товаров.
 *
 * <p>Методы возвращают {@link Mono}, а не готовые значения: страница собирается
 * минимум из двух запросов — выборки товаров и подсчёта количества в корзине, —
 * и раньше, пока второй не выполнится, отдавать всё равно нечего.
 *
 * <p>{@code @Transactional} продолжает работать и на реактивном стеке, но не
 * потому что аннотация универсальна, а потому что рядом существует бин
 * {@code R2dbcTransactionManager}: транзакция открывается при подписке на
 * возвращённый {@code Mono} и закрывается по его завершении.
 */
@Service
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final CartItemRepository cartItemRepository;

    public ItemService(ItemRepository itemRepository, CartItemRepository cartItemRepository) {
        this.itemRepository = itemRepository;
        this.cartItemRepository = cartItemRepository;
    }

    /**
     * Собирает страницу витрины.
     *
     * @param search     строка поиска, может быть пустой
     * @param sort       сортировка: {@code NO}, {@code ALPHA} или {@code PRICE}
     * @param pageNumber номер страницы, начиная с 1
     * @param pageSize   товаров на странице
     * @return страница витрины
     */
    public Mono<ItemsPage> getItems(String search, String sort, int pageNumber, int pageSize) {
        String searchForView = (search != null) ? search.trim() : "";
        String querySearch = !searchForView.isBlank() ? escapeLike(searchForView) : null;
        String normalizedSort = (sort == null || sort.isBlank()) ? "NO" : sort;
        int pageIndex = Math.max(pageNumber - 1, 0);
        int pageLimit = Math.max(pageSize, 1);
        long offset = (long) pageIndex * pageLimit;
        // В модель идёт нормализованный номер, а не присланный. При pageNumber = 0
        // смещение считается верно, но шаблон показал бы страницу 0 и «вперёд»
        // вместо первой страницы.
        int normalizedPageNumber = pageIndex + 1;

        Flux<Item> page;
        Mono<Long> total;
        if (querySearch != null) {
            page = switch (normalizedSort) {
                case "ALPHA" -> itemRepository.searchByTitle(querySearch, pageLimit, offset);
                case "PRICE" -> itemRepository.searchByPrice(querySearch, pageLimit, offset);
                default -> itemRepository.searchUnsorted(querySearch, pageLimit, offset);
            };
            total = itemRepository.countByText(querySearch);
        } else {
            page = switch (normalizedSort) {
                case "ALPHA" -> itemRepository.findAllByTitle(pageLimit, offset);
                case "PRICE" -> itemRepository.findAllByPrice(pageLimit, offset);
                default -> itemRepository.findAllUnsorted(pageLimit, offset);
            };
            total = itemRepository.count();
        }

        return page.collectList()
                .zipWith(total)
                .flatMap(pair -> withCartCounts(
                        pair.getT1(), pair.getT2(), searchForView, normalizedSort, normalizedPageNumber, pageLimit));
    }

    /**
     * Возвращает карточку товара вместе с количеством в корзине.
     *
     * @param id идентификатор товара
     * @return карточка товара
     */
    public Mono<ItemDto> getItem(long id) {
        return itemRepository
                .findById(id)
                .switchIfEmpty(Mono.error(() -> new ItemNotFoundException(id)))
                .flatMap(item -> cartItemRepository
                        .findByItemId(id)
                        .map(CartItem::getCount)
                        .defaultIfEmpty(0)
                        .map(count -> ItemDto.fromEntity(item, count)));
    }

    /**
     * Дописывает к товарам страницы количество в корзине и собирает страницу.
     *
     * <p>Позиции читаются по идентификаторам товаров страницы, а не по всей
     * корзине: условие {@code IN} без ограничения обошло бы весь каталог, а
     * шаблону нужны числа ровно у показанных товаров.
     *
     * @param items      товары страницы
     * @param total      всего товаров под поисковый запрос
     * @param search     строка поиска для модели
     * @param sort       вид сортировки для модели
     * @param pageNumber номер страницы, начиная с 1
     * @param pageLimit  товаров на странице
     * @return страница витрины
     */
    private Mono<ItemsPage> withCartCounts(
            List<Item> items, long total, String search, String sort, int pageNumber, int pageLimit) {
        if (items.isEmpty()) {
            return Mono.just(new ItemsPage(List.of(), search, sort, Paging.of(pageLimit, pageNumber, total)));
        }
        List<Long> itemIds = items.stream().map(Item::getId).toList();
        return cartItemRepository
                .findAllByItemIdIn(itemIds)
                .collectMap(CartItem::getItemId, CartItem::getCount)
                .map(counts -> {
                    List<ItemDto> dtos = items.stream()
                            .map(item -> ItemDto.fromEntity(item, counts.getOrDefault(item.getId(), 0)))
                            .toList();
                    return ItemsPage.of(dtos, search, sort, Paging.of(pageLimit, pageNumber, total));
                });
    }

    /**
     * Экранирует спецсимволы LIKE ({@code %}, {@code _}, {@code \}), чтобы они
     * не трактовались как шаблон поиска.
     *
     * @param text исходная строка
     * @return экранированная строка
     */
    private String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
