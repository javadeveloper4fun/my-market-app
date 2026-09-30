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
     * Ищет товары, у которых в названии или описании встречается переданный текст
     * (нечувствительно к регистру). Для получения всех товаров без фильтра
     * используется унаследованный {@code findAll(Pageable)}.
     *
     * @param text     искомая подстрока (не должна быть {@code null})
     * @param pageable настройки страницы и сортировки
     * @return страница найденных товаров
     */
    @Query("""
            select i from Item i
            where lower(i.title) like lower(concat('%', :text, '%')) escape '\\'
               or lower(i.description) like lower(concat('%', :text, '%')) escape '\\'
            """)
    Page<Item> searchByText(@Param("text") String text, Pageable pageable);
}
