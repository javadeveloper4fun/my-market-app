package ru.yandex.practicum.mymarket.service;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.codec.multipart.FilePart;
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

    /**
     * Префикс пути к картинке в поле {@code img_path}.
     *
     * <p>Значение совпадает с тем, что заносит сид-миграция спринта 5, и с тем,
     * что подставляет шаблон: {@code items.html} строит адрес как
     * {@code "/" + imgPath}. Совпадение позволяет отдавать и сид-картинки, и
     * загруженные одним и тем же маршрутом {@code /images/**}, без правки
     * уже применённых changeset'ов.
     */
    private static final String IMAGE_PATH_PREFIX = "images/";

    private final ItemRepository itemRepository;
    private final CartItemRepository cartItemRepository;
    private final ImageStorageService imageStorageService;

    public ItemService(
            ItemRepository itemRepository,
            CartItemRepository cartItemRepository,
            ImageStorageService imageStorageService) {
        this.itemRepository = itemRepository;
        this.cartItemRepository = cartItemRepository;
        this.imageStorageService = imageStorageService;
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
     * Создаёт товар с загруженной картинкой.
     *
     * <p>Порядок операций выбран из-за того, что хранилище не участвует в
     * транзакции базы. Сначала картинка кладётся в хранилище, потом товар
     * вставляется в базу. Обратный порядок опаснее: если вставка не пройдёт,
     * товара в базе не будет, а объект в хранилище уже будет, и удалять его
     * придётся отдельно.
     *
     * <p>При неудачной вставке объект удаляется как компенсирующее действие.
     * Это компенсация, а не откат: хранилище о транзакции базы не знает и
     * откатиться вместе с ней не может. Из двух возможных остатков — лишний
     * объект в хранилище или товар со ссылкой на несуществующую картинку —
     * хуже первый: он никому не виден. Неудача самого удаления только
     * логируется и наружу не идёт, чтобы не замаскировать исходную ошибку.
     *
     * <p>Картинка обязательна: пустую ссылку витрина не рисует, и товар без
     * неё был бы битой карточкой.
     *
     * <p>Тема 2/11 «Реактивное программирование на Java», п. «Обработка
     * ошибок» и «Устойчивость»: {@code onErrorResume} перехватывает отказ
     * базы, выполняет компенсирующее действие и возвращает исходную ошибку
     * наружу — система продолжает работать после сбоя, а не роняет запрос.
     *
     * @param title       название товара
     * @param description описание товара
     * @param price       цена в рублях
     * @param image       загружаемая картинка
     * @return созданный товар; количество в корзине равно нулю
     */
    @Transactional
    public Mono<ItemDto> createItem(String title, String description, long price, FilePart image) {
        return imageStorageService
                .upload(image)
                .flatMap(key -> itemRepository
                        .save(new Item(title, description, IMAGE_PATH_PREFIX + key, BigDecimal.valueOf(price)))
                        .onErrorResume(error -> imageStorageService.delete(key).then(Mono.error(error)))
                        .flatMap(saved -> cartItemRepository
                                .findByItemId(saved.getId())
                                .map(CartItem::getCount)
                                .defaultIfEmpty(0)
                                .map(count -> ItemDto.fromEntity(saved, count))));
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
