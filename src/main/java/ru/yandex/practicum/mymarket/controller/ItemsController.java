package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
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
     * Отображает главную страницу витрины с пагинацией, поиском и сортировкой.
     *
     * @param pageNumber номер страницы
     * @param pageSize   размер страницы
     * @param search     строка поиска
     * @param sort       тип сортировки
     * @param model      модель представления
     * @return имя шаблона витрины
     */
    @GetMapping({"/", "/items"})
    public String getItems(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "NO") String sort,
            Model model) {
        ItemsPage itemsPage = itemService.getItems(search, sort, pageNumber, pageSize);
        model.addAttribute("items", itemsPage.rows());
        model.addAttribute("search", search);
        model.addAttribute("sort", sort);
        model.addAttribute("paging", itemsPage.paging());
        return "items";
    }

    /**
     * Изменяет количество товара в корзине со страницы витрины.
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
    public String changeItemCount(
            @RequestParam long id,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "NO") String sort,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam Action action) {

        cartService.applyAction(id, action);

        String url = UriComponentsBuilder.fromPath("/items")
                .queryParam("search", search)
                .queryParam("sort", sort)
                .queryParam("pageNumber", pageNumber)
                .queryParam("pageSize", pageSize)
                .build()
                .encode()
                .toUriString();
        return "redirect:" + url;
    }

    /**
     * Отображает карточку конкретного товара.
     *
     * @param id идентификатор товара
     * @param model модель представления
     * @return имя шаблона карточки товара
     */
    @GetMapping("/items/{id}")
    public String getItem(@PathVariable long id, Model model) {
        ItemDto itemDto = itemService.getItem(id);
        model.addAttribute("item", itemDto);
        return "item";
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
    public String changeItemCountOnItemPage(@PathVariable long id, @RequestParam Action action, Model model) {
        cartService.applyAction(id, action);
        model.addAttribute("item", itemService.getItem(id));
        return "item";
    }
}
