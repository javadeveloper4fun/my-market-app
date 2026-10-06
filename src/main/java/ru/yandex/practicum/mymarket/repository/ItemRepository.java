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
     * Ищет товары, у которых в названии или описании (регистронезависимо) встречается
     * переданный текст: {@code lower(title) like lower(concat('%', :text, '%'))}
     * или {@code lower(description) like lower(concat('%', :text, '%'))}.
     * Экранирование символов `%` и `_` в тексте осуществляется вызывающей стороной
     * (метод {@link ItemService#escapeLike}) перед вызовом репозитория;
     * в JPQL объявлен {@code escape '\\'}, который интерпретирует '\\' как литерал.
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
