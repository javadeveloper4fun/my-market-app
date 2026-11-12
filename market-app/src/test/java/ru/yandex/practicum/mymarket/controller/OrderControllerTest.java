package ru.yandex.practicum.mymarket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.service.EmptyCartException;
import ru.yandex.practicum.mymarket.service.OrderNotFoundException;
import ru.yandex.practicum.mymarket.service.OrderService;

/**
 * Слайсовые тесты контроллера заказов на {@link WebTestClient}.
 *
 * <p>{@code @WebFluxTest} вместо {@code @WebMvcTest}: контроллер работает на
 * WebFlux, и проверять его нужно через тот же стек, на котором он запущен.
 *
 * <p>Методы сервиса возвращают {@code Mono}, поэтому заглушки отдают
 * {@code Mono.just(...)}. Заглушка, отдающая «голое» значение, осталась бы
 * ожидаемым результатом, который ни разу не будет использован: {@code thenReturn}
 * подпишется на {@code null} и упадёт уже в тесте.
 */
@WebFluxTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private OrderService orderService;

    /**
     * Проверяет, что GET /orders отдаёт страницу списка заказов.
     */
    @Test
    void getOrdersRendersOrdersView() {
        OrderDto order = new OrderDto(1L, List.of(), 7000L);
        when(orderService.getOrders()).thenReturn(Mono.just(List.of(order)));

        client.get()
                .uri("/orders")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Заказ"));
    }

    /**
     * Проверяет, что GET /orders/{id} отдаёт страницу заказа.
     */
    @Test
    void getOrderRendersOrderViewWithNewOrderFlag() {
        ItemDto item = new ItemDto(1L, "Куртка", "", "", 1500L, 2);
        OrderDto order = new OrderDto(1L, List.of(item), 3000L);
        when(orderService.getOrder(eq(1L))).thenReturn(Mono.just(order));

        client.get()
                .uri("/orders/1?newOrder=true")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Куртка"));
    }

    /**
     * Проверяет, что по умолчанию флаг newOrder равен false: без него страница
     * заказа отображается как обычная, а не как только что оформленная.
     */
    @Test
    void getOrderDefaultsNewOrderToFalse() {
        OrderDto order = new OrderDto(1L, List.of(), 0L);
        when(orderService.getOrder(eq(1L))).thenReturn(Mono.just(order));

        client.get()
                .uri("/orders/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).doesNotContain("Поздравляем"));
    }

    /**
     * Проверяет, что POST /buy перенаправляет на страницу нового заказа.
     */
    @Test
    void buyRedirectsToNewOrder() {
        when(orderService.buy()).thenReturn(Mono.just(42L));

        client.post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .location("/orders/42?newOrder=true");
    }

    /**
     * Проверяет, что POST /buy при пустой корзине перенаправляет в корзину.
     */
    @Test
    void buyRedirectsToCartWhenCartIsEmpty() {
        when(orderService.buy()).thenReturn(Mono.error(new EmptyCartException("Cart is empty")));

        client.post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .location("/cart/items");
    }

    /**
     * Проверяет, что ошибка, не являющаяся пустой корзиной, доходит до
     * пользователя и не подменяется редиректом в корзину.
     *
     * <p>Контроллер ловит только {@code EmptyCartException}. Если бы он ловил общий
     * {@code IllegalStateException}, эта ошибка тоже дала бы редирект в корзину и
     * настоящая причина сбоя была бы скрыта.
     */
    @Test
    void buyDoesNotRedirectOnUnexpectedIllegalState() {
        when(orderService.buy()).thenReturn(Mono.error(new IllegalStateException("connection lost")));

        client.post().uri("/buy").exchange().expectStatus().is5xxServerError();
    }

    /**
     * Проверяет, что несуществующий заказ даёт 404, а не 500.
     */
    @Test
    void getOrderReturns404WhenNotFound() {
        when(orderService.getOrder(eq(999L))).thenReturn(Mono.error(new OrderNotFoundException(999L)));

        client.get().uri("/orders/999").exchange().expectStatus().isNotFound();
    }
}
