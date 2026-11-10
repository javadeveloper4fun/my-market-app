package ru.yandex.practicum.mymarket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.Action;
import ru.yandex.practicum.mymarket.service.CartService;

/**
 * Слайсовые тесты контроллера корзины на {@link WebTestClient}.
 *
 * <p>{@code @WebFluxTest} вместо {@code @WebMvcTest}: контроллер работает на
 * WebFlux, и проверять его нужно через тот же стек, на котором он запущен.
 * MockMvc проверяет сервлетный конвейер, которого в приложении больше нет.
 */
@WebFluxTest(CartController.class)
class CartControllerTest {

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private CartService cartService;

    /**
     * Проверяет, что GET /cart/items отдаёт страницу корзины с атрибутами
     * items и total.
     */
    @Test
    void getCartRendersCartView() {
        ItemDto item = new ItemDto(1L, "Куртка", "Описание", "images/jacket.jpg", 4500L, 2);
        when(cartService.getCart()).thenReturn(Mono.just(new CartPage(List.of(item), 9000L)));

        client.get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Куртка"));
    }

    /**
     * Проверяет, что POST /cart/items отдаёт страницу корзины после изменения.
     *
     * <p>Параметры передаются в query-строке, а не в теле: в WebFlux
     * {@code @RequestParam} читает только query-строку, тогда как в MVC он читал
     * ещё и тело формы. Отсюда и форма в шаблоне: кнопки несут параметры в
     * атрибуте {@code formaction}.
     */
    @Test
    void postCartRendersCartView() {
        when(cartService.applyAction(eq(1L), eq(Action.DELETE))).thenReturn(Mono.empty());
        when(cartService.getCart()).thenReturn(Mono.just(new CartPage(List.of(), 0L)));

        client.post()
                .uri("/cart/items?id=1&action=DELETE")
                .exchange()
                .expectStatus()
                .isOk();

        verify(cartService).applyAction(eq(1L), eq(Action.DELETE));
    }

    /**
     * Проверяет, что невалидный action даёт 400, а не 500.
     */
    @Test
    void postCartRejectsInvalidAction() {
        client.post()
                .uri("/cart/items?id=1&action=BOGUS")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    /**
     * Проверяет, что запрос без обязательного параметра даёт 400.
     */
    @Test
    void postCartRejectsMissingAction() {
        client.post().uri("/cart/items?id=1").exchange().expectStatus().isBadRequest();
    }
}
