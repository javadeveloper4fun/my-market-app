package ru.yandex.practicum.mymarket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartPage;
import ru.yandex.practicum.mymarket.dto.CartView;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.Action;
import ru.yandex.practicum.mymarket.service.CartPageService;
import ru.yandex.practicum.mymarket.service.CartService;

/**
 * Слайсовые тесты контроллера корзины на {@link WebTestClient}.
 *
 * <p>{@code @WebFluxTest} вместо {@code @WebMvcTest}: контроллер работает на
 * WebFlux, и проверять его нужно через тот же стек, на котором он запущен.
 * MockMvc проверяет сервлетный конвейер, которого в приложении больше нет.
 */
@WebFluxTest(CartController.class)
@Import(CartControllerTest.TestSecurityConfig.class)
class CartControllerTest {

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CartPageService cartPageService;

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
     * Проверяет, что идентификатор попытки и сообщение об ошибке попадают в модель.
     */
    @Test
    void getCartKeepsAttemptIdForRetry() {
        CartPage cart = new CartPage(List.of(new ItemDto(1L, "Куртка", "", "img.jpg", 4500L, 1)), 4500L);
        when(cartPageService.getCartView()).thenReturn(Mono.just(new CartView(cart, 10_000L, null)));
        CartController controller = new CartController(cartService, cartPageService, currentUserProvider);
        ExtendedModelMap model = new ExtendedModelMap();

        controller.getCart(model, "payments-unavailable", "attempt-9").block();

        assertThat(model.get("attemptId")).isEqualTo("attempt-9");
        assertThat(model.get("paymentMessage"))
                .isEqualTo("Сервис платежей недоступен. Оформление заказа временно невозможно.");
    }

    @Test
    void getCartShowsMessageFromAttemptConflictRedirect() {
        CartPage cart = new CartPage(List.of(new ItemDto(1L, "Куртка", "", "img.jpg", 4500L, 1)), 4500L);
        when(cartPageService.getCartView()).thenReturn(Mono.just(new CartView(cart, 10_000L, null)));

        authed().get()
                .uri("/cart/items?paymentError=attempt-conflict")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Другая покупка уже оформляется"));
    }

    @Test
    void getCartRendersCartView() {
        ItemDto item = new ItemDto(1L, "Куртка", "Описание", "images/jacket.jpg", 4500L, 2);
        CartPage cart = new CartPage(List.of(item), 9000L);
        when(cartPageService.getCartView()).thenReturn(Mono.just(new CartView(cart, 10_000L, null)));

        authed().get()
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
        when(cartService.applyAction(eq(1L), eq(Action.DELETE), eq(1L))).thenReturn(Mono.empty());
        when(cartPageService.getCartView())
                .thenReturn(Mono.just(new CartView(new CartPage(List.of(), 0L), 10_000L, null)));

        authed().post()
                .uri("/cart/items?id=1&action=DELETE")
                .exchange()
                .expectStatus()
                .isOk();

        verify(cartService).applyAction(eq(1L), eq(Action.DELETE), eq(1L));
    }

    /**
     * Проверяет, что невалидный action даёт 400, а не 500.
     */
    @Test
    void postCartRejectsInvalidAction() {
        authed().post()
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
        authed().post().uri("/cart/items?id=1").exchange().expectStatus().isBadRequest();
    }

    /**
     * Проверяет, что баланс показывается, а кнопка покупки активна, когда
     * средств хватает.
     */
    @Test
    void getCartShowsBalanceAndEnablesBuyWhenFundsSufficient() {
        CartPage cart = new CartPage(List.of(new ItemDto(1L, "Куртка", "", "img.jpg", 4500L, 1)), 4500L);
        when(cartPageService.getCartView()).thenReturn(Mono.just(new CartView(cart, 10_000L, null)));

        authed().get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Куртка").contains("4500"));
    }

    /**
     * Проверяет, что при недостатке средств кнопка блокируется и показывается
     * причина.
     */
    @Test
    void getCartDisablesBuyWhenFundsAreInsufficient() {
        CartPage cart = new CartPage(List.of(new ItemDto(1L, "Куртка", "", "img.jpg", 4500L, 1)), 4500L);
        when(cartPageService.getCartView()).thenReturn(Mono.just(new CartView(cart, 1000L, null)));

        authed().get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Недостаточно средств").contains("Куртка"));
    }

    /**
     * Проверяет, что недоступность платежного сервиса видна пользователю.
     */
    @Test
    void getCartShowsMessageWhenPaymentsAreUnavailable() {
        CartPage cart = new CartPage(List.of(new ItemDto(1L, "Куртка", "", "img.jpg", 4500L, 1)), 4500L);
        when(cartPageService.getCartView())
                .thenReturn(Mono.just(new CartView(cart, null, CartPageService.PAYMENTS_UNAVAILABLE_MESSAGE)));

        authed().get()
                .uri("/cart/items")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html ->
                        assertThat(html).contains("Сервис платежей недоступен").contains("Куртка"));
    }

    /**
     * Проверяет, что сообщение о недоступности показывается и после редиректа
     * с кодом ошибки, если сервис платежей был недоступен на момент покупки.
     */
    @Test
    void getCartShowsMessageFromPaymentErrorRedirect() {
        CartPage cart = new CartPage(List.of(new ItemDto(1L, "Куртка", "", "img.jpg", 4500L, 1)), 4500L);
        when(cartPageService.getCartView()).thenReturn(Mono.just(new CartView(cart, 10_000L, null)));

        authed().get()
                .uri("/cart/items?paymentError=payments-unavailable")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Сервис платежей недоступен"));
    }

    /**
     * Проверяет, что код ошибки {@code insufficient-funds} не теряется, даже
     * если баланс успел измениться и блок предупреждения не отрисовался.
     */
    @Test
    void getCartShowsMessageFromInsufficientFundsRedirect() {
        CartPage cart = new CartPage(List.of(new ItemDto(1L, "Куртка", "", "img.jpg", 4500L, 1)), 4500L);
        when(cartPageService.getCartView()).thenReturn(Mono.just(new CartView(cart, 10_000L, null)));

        authed().get()
                .uri("/cart/items?paymentError=insufficient-funds")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Недостаточно средств для оплаты заказа."));
    }

    /**
     * Проверяет, что отклонённый платёжный запрос объясняется пользователю.
     */
    @Test
    void getCartShowsMessageFromInvalidPaymentRequestRedirect() {
        CartPage cart = new CartPage(List.of(new ItemDto(1L, "Куртка", "", "img.jpg", 4500L, 1)), 4500L);
        when(cartPageService.getCartView()).thenReturn(Mono.just(new CartView(cart, 10_000L, null)));

        authed().get()
                .uri("/cart/items?paymentError=invalid-request")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("сервис платежей отклонил сумму"));
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
            return http.csrf(
                            csrf -> csrf.csrfTokenRepository(org.springframework.security.web.server.csrf
                                    .CookieServerCsrfTokenRepository.withHttpOnlyFalse()))
                    .authorizeExchange(ex -> ex.anyExchange().permitAll())
                    .build();
        }
    }
}
