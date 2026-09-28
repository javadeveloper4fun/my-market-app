package ru.yandex.practicum.mymarket.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Товар, представленный на витрине магазина.
 */
@Entity
@Table(name = "items")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String imgPath;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Создаёт товар с заданными характеристиками.
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
