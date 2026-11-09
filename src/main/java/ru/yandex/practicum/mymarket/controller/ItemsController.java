package ru.yandex.practicum.mymarket.controller;

import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.NewItemForm;
import ru.yandex.practicum.mymarket.service.Action;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;

/**
 * Контроллер витрины товаров.
 *
 * <p>Тема 8/11 «Фреймворк WebFlux в Spring», урок 4/5: обработка входящего
 * запроса сосредоточена в {@code DispatcherHandler} — он отвечает за поиск
 * обработчика и преобразование ответа. Это аналог {@code DispatcherServlet} из
 * Spring MVC, то есть реализация паттерна Front Controller, только на
 * реактивном стеке. Класс помечен той же аннотацией {@code @Controller} из
 * {@code org.springframework.stereotype}, что и в блокирующей версии: при переходе
 * на WebFlux пришлось менять не разметку, а типы возвращаемых значений.
 *
 * <p>Тема 9/11 «Обработка запросов в функциональном стиле в WebFlux», уроки 2–3:
 * {@code DispatcherHandler} ищет обработчик через {@code HandlerMapping}, а среди
 * реализаций есть две — {@code RequestMappingHandlerMapping} для аннотированных
 * контроллеров и {@code RouterFunctionMapping} для функциональных эндпоинтов.
 * Запросы этого контроллера находит первая, потому что адреса описаны
 * аннотациями.
 */
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
     *
     * <p>Тема 2/11 «Реактивное программирование на Java», п. «Издатель и
     * подписчик»: контроллер возвращает {@link Mono}, а не готовую страницу.
     * Цепочка подписывается при отправке ответа, и поток не занимается в
     * ожидании базы — он переключается на другие запросы, что и описывает
     * пункт «Отзывчивость».
     *
     * <p>Тема 8/11, урок 5/5: в WebFlux вместо {@code ModelAndView} используется
     * интерфейс {@code Rendering}, однако {@link Model} продолжает работать,
     * поэтому атрибуты добавляются в него как обычно. Возвращается
     * {@code Mono<String>} — имя шаблона, а не готовая страница: обработчик
     * результата подписывается на реактивный тип, и страница приходит позже,
     * в отдельном потоке.
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
                    model.addAttribute("placeholderId", ItemDto.PLACEHOLDER_ID);
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
     * Отображает форму добавления товара.
     *
     * <p>Маршрут {@code /items/new} выбран потому, что {@code POST /items} уже
     * занят изменением количества в корзине: оба запроса идут на один адрес
     * разными методами, и под одним адресом они не ужились бы.
     *
     * @return имя шаблона формы
     */
    @GetMapping("/items/new")
    public Mono<String> getNewItemForm() {
        return Mono.just("item-new");
    }

    /**
     * Создаёт товар с загруженной картинкой.
     *
     * <p>После успешного добавления — редирект на витрину по схеме
     * POST-Redirect-GET. Возврат на форму показал бы товар повторно, если
     * пользователь обновит страницу, и вторая отправка завела бы второй товар.
     *
     * <p>Поля формы приходят частями запроса, а не параметрами строки: форма
     * отправляется с {@code multipart/form-data}, где каждое поле является
     * частью, а {@code @RequestParam} читает только строку запроса.
     *
     * <p>Поля проверяются до вызова сервиса: пустая форма — это ошибка ввода
     * пользователя, а не сбой приложения, и отвечать на неё надлежит кодом 400
     * с текстом причины. Без проверки незаполненная цена распаковывалась бы из
     * обёртки {@code Long} в примитив и давала 500.
     *
     * @param form поля формы: название, описание и цена
     * @param image картинка товара
     * @return редирект на витрину
     */
    @PostMapping("/items/new")
    public Mono<String> createItem(@ModelAttribute NewItemForm form, @RequestPart("image") FilePart image) {
        form.validate();
        return itemService
                .createItem(form.getTitle(), form.getDescription(), form.getPrice(), image)
                .thenReturn("redirect:/items");
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
