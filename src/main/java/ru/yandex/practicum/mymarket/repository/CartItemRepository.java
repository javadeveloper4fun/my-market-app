package ru.yandex.practicum.mymarket.repository;

import java.util.Collection;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.CartItem;

/**
 * Хранилище позиций корзины покупателя.
 *
 * <p>Метода {@code findAllWithItem} здесь нет, и это следствие перехода на
 * R2DBC. В JPA он существовал, чтобы одним {@code join fetch} не плодить запрос
 * на каждую позицию. В R2DBC такого способа нет: {@code findAll()} отдаёт
 * плоские позиции, а данные товара добираются вторым запросом по
 * идентификаторам. Два запроса на всю корзину вместо одного на позицию дешевле,
 * чем был бы N+1.
 *
 * <p>Количества для витрины берутся не проекцией, а обычной выборкой позиций по
 * идентификаторам. Интерфейсные проекции в R2DBC сопоставляют столбцы по
 * стратегии именования, и при несовпадении имени геттер возвращает {@code null}
 * без всякой ошибки — на витрине это выглядело бы как «товар не в корзине».
 * Конструкторное выражение {@code select new ...} недоступно: запрос уходит в СУБД
 * дословно и отвергается как некорректный синтаксис.
 */
public interface CartItemRepository extends ReactiveCrudRepository<CartItem, Long> {

    /**
     * Возвращает позицию корзины по товару.
     *
     * @param itemId идентификатор товара
     * @return позиция корзины или пустой результат, если товара в корзине нет
     */
    Mono<CartItem> findByItemId(Long itemId);

    /**
     * Возвращает позиции корзины по переданным товарам.
     *
     * <p>Выборка намеренно ограничена списком идентификаторов: без него пришлось
     * бы читать всю корзину, тогда как на витрине нужны только товары текущей
     * страницы.
     *
     * @param itemIds идентификаторы товаров
     * @return поток позиций этих товаров
     */
    Flux<CartItem> findAllByItemIdIn(Collection<Long> itemIds);
}
