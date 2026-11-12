package ru.yandex.practicum.mymarket.dto;

/**
 * Поля формы добавления товара.
 *
 * <p>Класс, а не запись: разбор формы в WebFlux связывает поля через сеттеры,
 * и конструкторная привязка работает неодинаково в разных версиях каркаса.
 * Сеттеры здесь — требование механизма, а не стилистический выбор.
 *
 * <p>Отдельный тип нужен ещё и потому, что текстовые поля формы не приходят
 * как части запроса: при {@code multipart/form-data} файлом считается только
 * файл, а остальные поля доступны как данные формы, и объявлять их
 * {@code @RequestPart} нельзя — запрос отвечает 415.
 *
 * <p>Заполненность полей проверяет сам метод {@link #validate()}, и проверка
 * живёт здесь по практической причине, а не по разделению ответственности:
 * поле {@code price} — обёртка {@link Long}, и при передаче в метод сервиса
 * с примитивом {@code long} пустое значение распаковывалось бы в
 * {@link NullPointerException}. Пользователь, не заполнивший обязательное
 * поле, получал 500 вместо понятного отказа.
 */
public class NewItemForm {

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
     * Проверяет, что обязательные поля заполнены, а цена неотрицательна.
     *
     * <p>Проверка своя, а не аннотациями {@code @Valid}: стартер валидации в
     * проекте нет, а подключать целую зависимость ради трёх полей смысла не
     * имеет — тем более что сервисный слой всё равно принимает примитивы и
     * должен оставаться защищённым помимо веб-формы.
     *
     * <p>Отрицательная цена отсекается здесь же, а не только базой: колонка
     * цены ограничения не имеет, и товар с ценой {@code -100} сохранился бы,
     * а витрина показала бы его как товар, который можно купить.
     *
     * @throws InvalidItemFormException если обязательное поле не заполнено или цена отрицательна
     */
    public void validate() {
        if (title == null || title.isBlank()) {
            throw new InvalidItemFormException("Укажите название товара");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidItemFormException("Укажите описание товара");
        }
        if (price == null) {
            throw new InvalidItemFormException("Укажите цену товара");
        }
        if (price < 0) {
            throw new InvalidItemFormException("Цена не может быть отрицательной");
        }
    }
}
