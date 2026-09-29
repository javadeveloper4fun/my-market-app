package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.mymarket.model.Item;

/**
 * Репозиторий товаров витрины.
 */
public interface ItemRepository extends JpaRepository<Item, Long> {

    /**
     * Ищет товары, у которых в названии или описании встречается переданный текст.
     * Если {@code text} — {@code null}, поиск пропускается и возвращаются все товары.
     * Пагинация и сортировка задаются объектом {@link Pageable}.
     *
     * @param text     искомая подстрока (нечувствительно к регистру)
     * @param pageable настройки страницы и сортировки
     * @return страница найденных товаров
     */
    @Query("""
            select i from Item i
            where (:text is null
                or lower(i.title) like lower(concat('%', :text, '%')) escape '\\'
                or lower(i.description) like lower(concat('%', :text, '%')) escape '\\')
            """)
    Page<Item> search(@Param("text") String text, Pageable pageable);
}
