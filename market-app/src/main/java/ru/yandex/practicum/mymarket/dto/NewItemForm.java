package ru.yandex.practicum.mymarket.dto;

/**
 * Поля формы добавления товара.
 *
 * <p>Класс, а не запись: разбор формы в WebFlux связывает поля через сеттеры.
 * Текстовые поля передаются как части {@code multipart/form-data}, поэтому для
 * них используется привязка данных формы, а не {@code @RequestPart}.
 */
public class NewItemForm {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final long MAX_PRICE = 99_999_999L;

    private String title;

    private String description;

    private Long price;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    /**
     * Проверяет обязательность, длину и диапазон значений полей формы.
     *
     * <p>Проверка выполняется до передачи примитивной цены в сервис и до
     * сохранения в базу, чтобы пользователь получал понятную ошибку ввода вместо
     * ошибки приведения типа или нарушения ограничения схемы.
     *
     * @throws InvalidItemFormException если данные формы некорректны
     */
    public void validate() {
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedDescription = description == null ? "" : description.trim();

        if (normalizedTitle.isEmpty()) {
            throw new InvalidItemFormException("Укажите название товара");
        }
        if (normalizedTitle.length() > MAX_TITLE_LENGTH) {
            throw new InvalidItemFormException("Название товара должно быть не длиннее 255 символов");
        }
        if (normalizedDescription.isEmpty()) {
            throw new InvalidItemFormException("Укажите описание товара");
        }
        if (normalizedDescription.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidItemFormException("Описание товара должно быть не длиннее 255 символов");
        }
        if (price == null) {
            throw new InvalidItemFormException("Укажите цену товара");
        }
        if (price <= 0) {
            throw new InvalidItemFormException("Цена товара должна быть больше нуля");
        }
        if (price > MAX_PRICE) {
            throw new InvalidItemFormException("Цена товара слишком большая");
        }

        this.title = normalizedTitle;
        this.description = normalizedDescription;
    }
}
