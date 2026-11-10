package ru.yandex.practicum.mymarket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.service.Action;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemNotFoundException;
import ru.yandex.practicum.mymarket.service.ItemService;

/**
 * Слайсовые тесты контроллера витрины на {@link WebTestClient}.
 *
 * <p>{@code @WebFluxTest} вместо {@code @WebMvcTest}: контроллер работает на
 * WebFlux, и проверять его нужно через тот же стек, на котором он запущен.
 * MockMvc проверяет сервлетный конвейер, которого в приложении больше нет.
 *
 * <p>POST-запросы несут параметры в query-строке, а не в теле: в WebFlux
 * {@code @RequestParam} читает только query-строку, тогда как в MVC он читал ещё
 * и тело формы. Отсюда и форма в шаблоне: кнопки несут параметры в атрибуте
 * {@code formaction}.
 */
@WebFluxTest(ItemsController.class)
class ItemsControllerTest {

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private ItemService itemService;

    @MockitoBean
    private CartService cartService;

    /**
     * Проверяет, что GET / отдаёт витрину с атрибутами модели из сервиса.
     */
    @Test
    void getItemsRendersItemsView() {
        ItemsPage page = new ItemsPage(List.of(), "", "NO", new Paging(5, 1, false, false));
        when(itemService.getItems(eq(""), eq("NO"), eq(1), eq(5))).thenReturn(Mono.just(page));

        client.get()
                .uri("/")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).isNotEmpty());
    }

    /**
     * Проверяет, что GET /items передаёт параметры запроса в сервис без изменений.
     *
     * <p>Помимо модели проверяется и сам отрисованный HTML: наличие названия
     * товара в ответе ловит опечатки в шаблоне, которые не дают исключения и
     * молча приводят к пустой витрине.
     */
    @Test
    void getItemsPassesRequestParamsToService() {
        ItemDto dto = new ItemDto(1L, "Куртка", "Описание", "images/jacket.jpg", 4500L, 0);
        ItemsPage page = ItemsPage.of(List.of(dto), "куртка", "ALPHA", new Paging(5, 1, false, false));
        when(itemService.getItems(eq("куртка"), eq("ALPHA"), eq(1), eq(5))).thenReturn(Mono.just(page));

        client.get()
                .uri(URI.create(
                        "/items?search=%D0%BA%D1%83%D1%80%D1%82%D0%BA%D0%B0&sort=ALPHA&pageNumber=1&pageSize=5"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Куртка"));
    }

    /**
     * Проверяет, что POST /items перенаправляет на витрину, сохраняя параметры
     * запроса.
     */
    @Test
    void postItemsRedirectsWithPreservedParams() {
        when(cartService.applyAction(eq(1L), eq(Action.PLUS))).thenReturn(Mono.empty());

        client.post()
                .uri(URI.create("/items?id=1&action=PLUS&search=%D0%BA%D1%83%D1%80%D1%82%D0%BA%D0%B0"
                        + "&sort=ALPHA&pageNumber=2&pageSize=10"))
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .location("/items?search=%D0%BA%D1%83%D1%80%D1%82%D0%BA%D0%B0&sort=ALPHA&pageNumber=2&pageSize=10");

        verify(cartService).applyAction(eq(1L), eq(Action.PLUS));
    }

    /**
     * Проверяет, что GET /items/{id} отдаёт карточку товара.
     *
     * <p>Название товара проверяется в теле ответа, а не только в модели:
     * опечатка в выражении шаблона иначе оставила бы страницу пустой, и тест
     * остался бы зелёным.
     */
    @Test
    void getItemRendersItemView() {
        ItemDto dto = new ItemDto(1L, "Куртка", "Описание", "images/jacket.jpg", 4500L, 0);
        when(itemService.getItem(eq(1L))).thenReturn(Mono.just(dto));

        client.get()
                .uri("/items/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Куртка"));
    }

    /**
     * Проверяет, что POST /items/{id} отдаёт карточку товара после изменения.
     */
    @Test
    void postItemRendersItemView() {
        ItemDto dto = new ItemDto(1L, "Куртка", "Описание", "images/jacket.jpg", 4500L, 1);
        when(cartService.applyAction(eq(1L), eq(Action.MINUS))).thenReturn(Mono.empty());
        when(itemService.getItem(eq(1L))).thenReturn(Mono.just(dto));

        client.post().uri("/items/1?action=MINUS").exchange().expectStatus().isOk();

        verify(cartService).applyAction(eq(1L), eq(Action.MINUS));
    }

    /**
     * Проверяет, что невалидный action даёт 400, а не 500.
     */
    @Test
    void postItemRejectsInvalidAction() {
        client.post().uri("/items/1?action=UNKNOWN").exchange().expectStatus().isBadRequest();
    }

    /**
     * Проверяет, что карточка несуществующего товара даёт 404, а не 500.
     */
    @Test
    void getItemReturns404WhenNotFound() {
        when(itemService.getItem(eq(999L))).thenReturn(Mono.error(new ItemNotFoundException(999L)));

        client.get().uri("/items/999").exchange().expectStatus().isNotFound();
    }

    /**
     * Проверяет, что невалидный {@code action} на витрине даёт 400: раньше такая
     * ошибка попадала в обработчик «не найдено» и отдавала 404.
     */
    @Test
    void itemsWithInvalidActionReturns400() {
        client.post().uri("/items?action=UNKNOWN").exchange().expectStatus().isBadRequest();
    }

    /**
     * Проверяет, что настоящий некорректный аргумент даёт 400, а не 404.
     */
    @Test
    void itemWithInvalidIdTypeReturns400() {
        client.get().uri("/items/abc").exchange().expectStatus().isBadRequest();
    }
}
