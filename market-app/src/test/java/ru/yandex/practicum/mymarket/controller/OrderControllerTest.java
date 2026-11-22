package ru.yandex.practicum.mymarket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.payments.InsufficientFundsException;
import ru.yandex.practicum.mymarket.payments.InvalidPaymentRequestException;
import ru.yandex.practicum.mymarket.payments.PaymentsUnavailableException;
import ru.yandex.practicum.mymarket.service.CheckoutService;
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
@Import(OrderControllerTest.TestSecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CheckoutService checkoutService;

    @MockitoBean
    private ru.yandex.practicum.mymarket.security.CurrentUserProvider currentUserProvider;

    @org.junit.jupiter.api.BeforeEach
    void setUpCurrentUser() {
        ru.yandex.practicum.mymarket.model.User user = new ru.yandex.practicum.mymarket.model.User();
        user.setId(1L);
        user.setUsername("user1");
        when(currentUserProvider.getCurrentUser()).thenReturn(Mono.just(user));
    }

    private WebTestClient authed() {
        return client.mutateWith(mockUser("user1").roles("USER")).mutateWith(csrf());
    }

    /**
     * Проверяет, что GET /orders отдаёт страницу списка заказов.
     */
    @Test
    void getOrdersRendersOrdersView() {
        OrderDto order = new OrderDto(1L, List.of(), 7000L);
        when(orderService.getOrders(eq(1L))).thenReturn(Mono.just(List.of(order)));

        authed().get()
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
        when(orderService.getOrder(eq(1L), eq(1L))).thenReturn(Mono.just(order));

        authed().get()
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
        when(orderService.getOrder(eq(1L), eq(1L))).thenReturn(Mono.just(order));

        authed().get()
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
        when(checkoutService.checkout()).thenReturn(Mono.just(42L));

        authed().post()
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
        when(checkoutService.checkout()).thenReturn(Mono.error(new EmptyCartException("Cart is empty")));

        authed().post()
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
     * <p>Контроллер перехватывает только ожидаемые исключения — пустую корзину и
     * ошибки оплаты, — но не общий {@code IllegalStateException}. Если бы он ловил
     * общий тип, эта ошибка тоже дала бы редирект в корзину и настоящая причина
     * сбоя была бы скрыта.
     */
    @Test
    void buyDoesNotRedirectOnUnexpectedIllegalState() {
        when(checkoutService.checkout()).thenReturn(Mono.error(new IllegalStateException("connection lost")));

        authed().post().uri("/buy").exchange().expectStatus().is5xxServerError();
    }

    /**
     * Проверяет, что отказ платежа из-за недостатка средств ведёт в корзину.
     */
    @Test
    void buyRedirectsToCartWhenFundsAreInsufficient() {
        when(checkoutService.checkout()).thenReturn(Mono.error(new InsufficientFundsException()));

        authed().post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .location("/cart/items?paymentError=insufficient-funds");
    }

    /**
     * Проверяет, что отклонённый платёжный запрос не выдаётся за недоступность сервиса.
     */
    @Test
    void buyRedirectsToCartWhenPaymentRequestIsInvalid() {
        when(checkoutService.checkout())
                .thenReturn(Mono.error(new InvalidPaymentRequestException("bad amount", new IllegalStateException())));

        authed().post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .location("/cart/items?paymentError=invalid-request");
    }

    /**
     * Проверяет, что недоступность платежного сервиса ведёт в корзину с кодом.
     */
    @Test
    void buyRedirectsToCartWhenPaymentsAreUnavailable() {
        when(checkoutService.checkout()).thenReturn(Mono.error(new PaymentsUnavailableException("connection refused")));

        authed().post()
                .uri("/buy")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .expectHeader()
                .location("/cart/items?paymentError=payments-unavailable");
    }

    /**
     * Проверяет, что несуществующий заказ даёт 404, а не 500.
     */
    @Test
    void getOrderReturns404WhenNotFound() {
        when(orderService.getOrder(eq(999L), eq(1L))).thenReturn(Mono.error(new OrderNotFoundException(999L)));

        authed().get().uri("/orders/999").exchange().expectStatus().isNotFound();
    }

@TestConfiguration
    @EnableWebFluxSecurity
    static class TestSecurityConfig {
        @Bean
        SpringSecurityDialect springSecurityDialect() {
            return new SpringSecurityDialect();
        }

        @Bean
        SecurityWebFilterChain testChain() {
            ServerHttpSecurity http = ServerHttpSecurity.http();
            return http
                .csrf(csrf -> csrf.csrfTokenRepository(org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
        }
    }
}
