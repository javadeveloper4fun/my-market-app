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

        // collectList() материализует страницу: zipWith ниже должен получить
        // готовый список, а не поток, который ещё может отдавать элементы.
        return page.collectList()
                // zipWith ждёт оба источника — список товаров и total — и отдаёт
                // пару. Это аналог CompletableFuture.allOf, но без блокировки
                // потока: подписка на оба запроса происходит внутри Reactor.
                .zipWith(total)
                // Только после обеих выборок можно дописать количество в корзине
                // и собрать ItemsPage. До подписки на возвращённый Mono ни один
                // из запросов ещё не выполнен.
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
        // Mono ленив: запрос уйдёт в базу только при подписке на результат.
        return itemRepository
                .findById(id)
                // Пустой Mono означает «товара нет», но для карточки это ошибка.
                // switchIfEmpty превращает пустоту в ItemNotFoundException,
                // иначе контроллер получил бы пустой ответ вместо 404.
                .switchIfEmpty(Mono.error(() -> new ItemNotFoundException(id)))
                // Товар найден; теперь читаем его позицию в корзине. flatMap
                // нужен потому, что следующий шаг тоже асинхронный.
                .flatMap(item -> cartItemRepository
                        .findByItemId(id)
                        // Дальше нужен только счётчик, а не вся сущность CartItem.
                        .map(CartItem::getCount)
                        // Позиции в корзине может не быть — это 0, а не пустой
                        // результат: карточка товара всё равно должна отрисоваться.
                        .defaultIfEmpty(0)
                        // Собираем DTO из товара и количества; это последний
                        // шаг цепочки, подписку сделает вызывающий код.
                        .map(count -> ItemDto.fromEntity(item, count)));
    }

    /**
     * Создаёт товар с загруженной картинкой.
     *
     * <p>Картинка обязательна: пустую ссылку витрина не рисует, и товар без
     * неё был бы битой карточкой.
     *
     * <p>Загрузка картинки в S3 выполняется <em>вне</em> транзакции БД: сетевой
     * вызов к хранилищу удерживал бы соединение с БД открытым на всё время
     * загрузки, что непозволительно в событийном цикле WebFlux. Сначала
     * картинка кладётся в хранилище, затем полученный ключ передаёт в
     * {@link #saveWithImage(String, String, long, String)}, который уже работает
     * внутри {@code @Transactional}.
     *
     * @param title       название товара
     * @param description описание товара
     * @param price       цена в рублях
     * @param image       загружаемая картинка
     * @return созданный товар; количество в корзине равно нулю
     * @see #saveWithImage(String, String, long, String)
     */
    public Mono<ItemDto> createItem(String title, String description, long price, FilePart image) {
        // Тема 3/11, урок 1/3 «Спецификация Reactive Streams». Обратите внимание
        // на форму метода: несколько асинхронных шагов (загрузка в хранилище,
        // сохранение в БД, чтение из корзины, сборка DTO), и ни одного
        // блокирующего ожидания между ними. CompletableFuture здесь не годится —
        // он вернул бы всё сразу и не дал бы вклиниться между шагами. Порядок задан
        // операторами: flatMap выполняет следующий шаг на элементе предыдущего,
        // а ленивый Mono означает, что до подписки не выполняется ни загрузка
        // в хранилище, ни вставка в базу. Реализаций Publisher и Subscriber
        // в коде нет — обе создаёт Reactor, а цепочка описана объявлением
        // возвращаемого типа.
        //
        // Сначала картинка кладётся в хранилище, потом товар вставляется в базу.
        // Обратный порядок опаснее: если вставка не пройдёт, товара в базе не
        // будет, а объект в хранилище уже будет, и удалять его придётся отдельно.
        return imageStorageService.upload(image).flatMap(key -> saveWithImage(title, description, price, key));
    }

    /**
     * Сохраняет товар в БД с уже полученным ключом картинки внутри транзакции.
     *
     * <p>Транзакция открывается только на операцию записи в базу. Загрузка в S3
     * уже выполнена до вызова этого метода, поэтому соединение с БД не удерживается
     * во время сетевого вызова к хранилищу.
     *
     * <p>Тема 4/11 «Автоконфигурация и тестирование Spring WebFlux», урок 1/4:
     * {@code @Transactional} на реактивном стеке работает через
     * {@code R2dbcTransactionManager} — транзакция открывается при подписке на
     * возвращённый {@code Mono} и закрывается по его завершении.
     *
     * @param title       название товара
     * @param description описание товара
     * @param price       цена в рублях
     * @param key         ключ уже загруженной в S3 картинки
     * @return созданный товар; количество в корзине равно нулю
     */
    @Transactional
    public Mono<ItemDto> saveWithImage(String title, String description, long price, String key) {
        return itemRepository
                .save(new Item(title, description, IMAGE_PATH_PREFIX + key, BigDecimal.valueOf(price)))
                // Тема 2/11, п. «Обработка ошибок» и «Устойчивость»:
                // onErrorResume перехватывает отказ базы, выполняет
                // компенсирующее действие и возвращает исходную ошибку
                // наружу — система продолжает работать, а не роняет запрос.
                //
                // Это компенсация, а не откат: хранилище о транзакции базы
                // не знает и откатиться вместе с ней не может. Из двух
                // возможных остатков — лишний объект в хранилище или товар
                // со ссылкой на несуществующую картинку — хуже первый:
                // он никому не виден. Неудача удаления только логируется,
                // чтобы не замаскировать исходную ошибку.
                .onErrorResume(error -> imageStorageService.delete(key).then(Mono.error(error)))
                .flatMap(saved -> cartItemRepository
                        .findByItemId(saved.getId())
                        .map(CartItem::getCount)
                        // Товара в корзине нет — это 0, а не пустой результат:
                        // карточка товара всё равно должна отрисоваться.
                        .defaultIfEmpty(0)
                        .map(count -> ItemDto.fromEntity(saved, count)));
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
