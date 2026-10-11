package ru.yandex.practicum.mymarket.model;

import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Товар, доступный для просмотра и заказа.
 *
 * <p>В R2DBC сущность — обычный класс с {@code @Id}, без EntityManager и без
 * ленивой загрузки. Имя таблицы указано явно, потому что по умолчанию из класса
 * {@code Item} получилось бы {@code item}, а в схеме таблица называется
 * {@code items}. Остальные имена полей Spring Data приводит к snake_case сам,
 * поэтому {@code imgPath} ложится в колонку {@code img_path}.
 *
 * <p>{@code id} заполняет база: {@code save()} с {@code id == null} делает
 * INSERT и возвращает сгенерированный ключ.
 *
 * <p>Тема 5/11 «Концепция сущностей для работы с данными», урок 5/7: за маппинг
 * по умолчанию отвечает {@code MappingR2dbcConverter}, и в нём описаны
 * преобразования для примитивов и их ссылочных обёрток, {@code BigInteger},
 * {@code BigDecimal}, {@code UUID}, перечислений и временных типов — в обе
 * стороны. Поэтому {@code Long id} и {@code BigDecimal price} работают без
 * дополнительных настроек.
 */
@Table("items")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Item {

    @Id
    private Long id;

    private String title;

    private String description;

    private String imgPath;

    private BigDecimal price;

    /**
     * Создаёт товар, ещё не сохранённый в базе.
     *
     * @param title       название
     * @param description описание
     * @param imgPath     путь к изображению (без ведущего "/")
     * @param price       цена
     */
    public Item(String title, String description, String imgPath, BigDecimal price) {
        this.title = title;
        this.description = description;
        this.imgPath = imgPath;
        this.price = price;
    }
}
