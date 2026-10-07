package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.Action;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;

@Controller
public class ItemsController {

    private final ItemService itemService;
    private final CartService cartService;

    public ItemsController(ItemService itemService, CartService cartService) {
        this.itemService = itemService;
        this.cartService = cartService;
    }

    /**
     * GET /items — витрина товаров (Спринт 5, Тема 1, Урок 2).
     * Отображает главную страницу витрины с пагинацией, поиском и сортировкой.
     * Опциональные параметры: search, sort, pageNumber, pageSize.
     *
     * @param pageNumber номер страницы
     * @param pageSize   размер страницы
     * @param search     строка поиска
     * @param sort       тип сортировки
     * @param model      модель представления
     * @return имя шаблона витрины
     */
    @GetMapping({"/", "/items"})
    public Mono<String> getItems(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "NO") String sort,
            Model model) {
        return itemService
                .getItems(search, sort, pageNumber, pageSize)
                .doOnNext(page -> {
                    model.addAttribute("items", page.rows());
                    model.addAttribute("search", page.search());
                    model.addAttribute("sort", page.sort());
                    model.addAttribute("paging", page.paging());
                })
                .thenReturn("items");
    }

    /**
     * Изменяет количество товара в корзине со страницы витрины.
     *
     * <p>Адрес редиректа собирается без вызова {@code encode()}. Кодировать его
     * здесь не нужно: заголовок {@code Location} кодируется при отправке, и
     * предварительное кодирование приводит к двойному — «%D0%BA» превращается в
     * «%25D0%25BA», и поиск с кириллицей после перехода перестаёт работать.
     *
     * @param id         идентификатор товара
     * @param search     строка поиска (для сохранения в URL)
     * @param sort       сортировка (для сохранения в URL)
     * @param pageSize   размер страницы (для сохранения в URL)
     * @param pageNumber номер страницы (для сохранения в URL)
     * @param action     действие над товаром
     * @return редирект на витрину с сохранёнными параметрами
     */
    @PostMapping("/items")
    public Mono<String> changeItemCount(
            @RequestParam long id,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "NO") String sort,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam Action action) {

        String url = UriComponentsBuilder.fromPath("/items")
                .queryParam("search", search)
                .queryParam("sort", sort)
                .queryParam("pageNumber", pageNumber)
                .queryParam("pageSize", pageSize)
                .build()
                .toUriString();

        return cartService.applyAction(id, action).thenReturn("redirect:" + url);
    }

    /**
     * Отображает карточку конкретного товара.
     *
     * @param id    идентификатор товара
     * @param model модель представления
     * @return имя шаблона карточки товара
     */
    @GetMapping("/items/{id}")
    public Mono<String> getItem(@PathVariable long id, Model model) {
        return itemService
                .getItem(id)
                .doOnNext(item -> model.addAttribute("item", item))
                .thenReturn("item");
    }

    /**
     * Изменяет количество товара в корзине со страницы карточки товара.
     *
     * @param id     идентификатор товара
     * @param action действие над товаром
     * @param model  модель представления
     * @return имя шаблона карточки товара
     */
    @PostMapping("/items/{id}")
    public Mono<String> changeItemCountOnItemPage(@PathVariable long id, @RequestParam Action action, Model model) {
        return cartService
                .applyAction(id, action)
                .then(itemService.getItem(id))
                .doOnNext(item -> model.addAttribute("item", item))
                .thenReturn("item");
    }
}
