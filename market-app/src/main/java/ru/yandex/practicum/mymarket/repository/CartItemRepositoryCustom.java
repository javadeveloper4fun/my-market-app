package ru.yandex.practicum.mymarket.repository;

import reactor.core.publisher.Mono;

/**
 * Пользовательский фрагмент репозитория корзины с диалектозависимым UPSERT.
 */
public interface CartItemRepositoryCustom {

    /**
     * Атомарно увеличивает количество товара в корзине пользователя на 1.
     *
     * <p>Если строки для пары {@code (item_id, user_id)} ещё нет, создаёт её с {@code count = 1}.
     *
     * @param itemId идентификатор товара
     * @param userId идентификатор пользователя
     * @return число затронутых строк
     */
    Mono<Integer> upsertIncreaseCount(long itemId, Long userId);
}
