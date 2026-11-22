package ru.yandex.practicum.mymarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.payments.PaymentsGateway;
import ru.yandex.practicum.mymarket.payments.PaymentsUnavailableException;

/**
 * Тесты сборки страницы корзины: баланс, доступность кнопки и сообщения.
 *
 * <p>Проверка идёт без Spring-контекста: и корзина, и шлюз платежей здесь
 * заглушки, а предметный интерес представляет только логика
 * {@link CartPageService} — превращение двух ответов в состояние страницы.
 */
@ExtendWith(MockitoExtension.class)
class CartPageServiceTest {

    @Mock
    private CartService cartService;

    @Mock
    private PaymentsGateway paymentsGateway;

    @InjectMocks
    private CartPageService cartPageService;

    @Test
    void allowsBuyWhenBalanceCoversTotal() {
        when(cartService.getCart()).thenReturn(Mono.just(cart(4500L)));
        when(paymentsGateway.getBalance()).thenReturn(Mono.just(10_000L));

        var view = cartPageService.getCartView().block();

        assertThat(view.canPay()).isTrue();
        assertThat(view.insufficientFunds()).isFalse();
        assertThat(view.balance()).isEqualTo(10_000L);
        assertThat(view.paymentError()).isNull();
    }

    @Test
    void blocksBuyWhenBalanceIsLessThanTotal() {
        when(cartService.getCart()).thenReturn(Mono.just(cart(4500L)));
        when(paymentsGateway.getBalance()).thenReturn(Mono.just(1000L));

        var view = cartPageService.getCartView().block();

        assertThat(view.canPay()).isFalse();
        assertThat(view.insufficientFunds()).isTrue();
        assertThat(view.balance()).isEqualTo(1000L);
    }

    @Test
    void blocksBuyWhenPaymentsAreUnavailable() {
        when(cartService.getCart()).thenReturn(Mono.just(cart(4500L)));
        when(paymentsGateway.getBalance())
                .thenReturn(Mono.error(new PaymentsUnavailableException("connection refused")));

        var view = cartPageService.getCartView().block();

        assertThat(view.canPay()).isFalse();
        assertThat(view.balance()).isNull();
        assertThat(view.paymentError()).isNotBlank();
    }

    @Test
    void blocksBuyWhenCartIsEmpty() {
        when(cartService.getCart()).thenReturn(Mono.just(new CartPage(List.of(), 0L)));
        when(paymentsGateway.getBalance()).thenReturn(Mono.just(10_000L));

        var view = cartPageService.getCartView().block();

        assertThat(view.canPay()).isFalse();
        assertThat(view.insufficientFunds()).isFalse();
    }

    private CartPage cart(long total) {
        return new CartPage(List.of(new ItemDto(1L, "Куртка", "", "img.jpg", total, 1)), total);
    }
}
