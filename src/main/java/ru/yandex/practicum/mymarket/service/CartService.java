package ru.yandex.practicum.mymarket.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

/**
 * Корзина покупателя.
 *
 * <p>Позиции и товары хранятся в разных таблицах, и в блокирующей версии они
 * читались одним {@code join fetch}. На реактивном стеке это два запроса: сначала
 * позиции, затем товары по найденным идентификаторам. Порядок позиций
 * корзины сохраняется тем, что итоговый список собирается перебором позиций, а
 * не товаров: иначе выдача шла бы в порядке таблицы {@code items} и корзина
 * «переставлялась» сама.
 *
 * <p>Тема 4/11 «Реактивный доступ к данным в Spring Data R2DBC», раздел
 * «Декларативное управление транзакциями через {@code @Transactional}»:
 * аннотация работает за счёт бина {@code R2dbcTransactionManager}, а не
 * {@code DataSourceTransactionManager}, поэтому на реактивном стеке менеджер
 * другой. Транзакция открывается при подписке на возвращённый {@code Mono} и
 * закрывается по его завершении — то есть позже, чем вызов метода. Как и в
 * блокирующей версии, действует только на публичные методы: прокси
 * не переопределяет private-методы.
 */
@Service
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ItemRepository itemRepository;

    public CartService(CartItemRepository cartItemRepository, ItemRepository itemRepository) {
        this.cartItemRepository = cartItemRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * Возвращает корзину покупателя.
     *
     * <p>Итоговая сумма считается по усечённым длинным ценам, как и на витрине:
     * {@link ItemDto} уже хранит целые рубли, поэтому дополнительного округления
     * не происходит.
     *
     * @return корзина со списком позиций и суммой
     */
    public Mono<CartPage> getCart() {
        return cartItemRepository.findAll().collectList().flatMap(cartItems -> {
            if (cartItems.isEmpty()) {
                return Mono.just(new CartPage(List.of(), 0L));
            }
            List<Long> itemIds = cartItems.stream().map(CartItem::getItemId).toList();
            return itemRepository.findAllById(itemIds).collectList().map(items -> toPage(cartItems, items));
        });
    }

    /**
     * Применяет действие (увеличить/уменьшить/удалить) к позиции товара в корзине.
     *
     * <p>Здесь важна тонкость реактивных цепочек. Действие возвращает
     * {@code Mono<Void>}, а {@code Mono<Void>} завершается пустым — так устроен
     * {@code then()}. Поэтому {@code switchIfEmpty} нельзя ставить после
     * {@code flatMap}: он увидит пустой результат даже у найденной позиции и
     * решит, что товара в корзине нет, после чего попытается вставить вторую
     * строку и упрётся в уникальный индекс по {@code item_id}. Отсутствие
     * позиции поэтому передаётся дальше отдельным значением, а не «пустым
     * результатом в конце цепочки».
     *
     * <p>Если товара в корзине нет, позиция создаётся только при {@code PLUS} с
     * количеством 1; для остальных действий пустой результат — корректный ответ,
     * а не ошибка: уменьшать или удалять нечего.
     *
     * @param itemId идентификатор товара
     * @param action действие над позицией
     * @return завершение операции
     */
    @Transactional
    public Mono<Void> applyAction(long itemId, Action action) {
        return cartItemRepository
                .findByItemId(itemId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(found -> found.map(cartItem -> applyToExisting(cartItem, action))
                        .orElseGet(() -> createWhenAbsent(itemId, action)))
                .then();
    }

    /**
     * Применяет действие к уже существующей позиции.
     *
     * @param cartItem позиция корзины
     * @param action   действие над позицией
     * @return завершение операции
     */
    private Mono<Void> applyToExisting(CartItem cartItem, Action action) {
        return switch (action) {
            case PLUS -> saveWithCount(cartItem, cartItem.getCount() + 1);
            case MINUS ->
                cartItem.getCount() <= 1
                        ? cartItemRepository.delete(cartItem)
                        : saveWithCount(cartItem, cartItem.getCount() - 1);
            case DELETE -> cartItemRepository.delete(cartItem);
        };
    }

    /**
     * Создаёт позицию с количеством 1, если действие — {@code PLUS}.
     *
     * <p>Товар ищется отдельно: позиция ссылается на {@code item_id}, и в R2DBC
     * вложенная ссылка при записи заставила бы сохранить ещё и сам товар.
     *
     * @param itemId идентификатор товара
     * @param action действие над позицией
     * @return завершение операции
     */
    private Mono<Void> createWhenAbsent(long itemId, Action action) {
        if (action != Action.PLUS) {
            return Mono.empty();
        }
        return itemRepository
                .findById(itemId)
                .switchIfEmpty(Mono.error(() -> new ItemNotFoundException(itemId)))
                .flatMap(item -> cartItemRepository.save(new CartItem(item.getId(), 1)))
                .then();
    }

    /**
     * Сохраняет позицию с заданным количеством.
     *
     * @param cartItem позиция корзины
     * @param count    новое количество
     * @return завершение операции
     */
    private Mono<Void> saveWithCount(CartItem cartItem, int count) {
        cartItem.setCount(count);
        return cartItemRepository.save(cartItem).then();
    }

    /**
     * Собирает страницу корзины, сохраняя порядок позиций.
     *
     * @param cartItems позиции корзины в порядке выдачи
     * @param items     товары по идентификаторам позиций
     * @return корзина со списком позиций и суммой
     */
    private CartPage toPage(List<CartItem> cartItems, List<Item> items) {
        Map<Long, Item> byId = items.stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        List<ItemDto> dtos = cartItems.stream()
                .filter(cartItem -> byId.containsKey(cartItem.getItemId()))
                .map(cartItem -> ItemDto.fromEntity(byId.get(cartItem.getItemId()), cartItem.getCount()))
                .toList();
        long total = dtos.stream().mapToLong(dto -> dto.price() * dto.count()).sum();
        return new CartPage(dtos, total);
    }
}
