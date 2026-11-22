package ru.yandex.practicum.mymarket.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payments.InsufficientFundsException;
import ru.yandex.practicum.mymarket.payments.InvalidPaymentRequestException;
import ru.yandex.practicum.mymarket.payments.PaymentsUnavailableException;
import ru.yandex.practicum.mymarket.service.CheckoutService;
import ru.yandex.practicum.mymarket.service.EmptyCartException;
import ru.yandex.practicum.mymarket.service.OrderService;

/**
 * Заказы покупателя: список заказов, карточка заказа и оформление.
 *
 * <p>Контроллер возвращает {@link Mono} с именем шаблона Thymeleaf, а не готовую
 * страницу. Оформление заказа ({@code /buy}) делегируется {@link CheckoutService}:
 * снимок корзины с фиксацией цен, оплата вне транзакции, затем сохранение заказа
 * и корректировка купленных позиций корзины в одной транзакции. Такой порядок не
 * создаёт заказ без оплаты и не теряет товар, добавленный после снимка корзины.
 */
@Controller
public class OrderController {

    private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final CheckoutService checkoutService;

    public OrderController(OrderService orderService, CheckoutService checkoutService) {
        this.orderService = orderService;
        this.checkoutService = checkoutService;
    }

    /**
     * Отдаёт страницу со списком заказов.
     *
     * @param model модель представления
     * @return имя шаблона списка заказов
     */
    @GetMapping("/orders")
    public Mono<String> getOrders(Model model) {
        return orderService
                .getOrders()
                .doOnNext(orders -> model.addAttribute("orders", orders))
                .thenReturn("orders");
    }

    /**
     * Отдаёт страницу совершённого заказа.
     *
     * @param id       идентификатор заказа
     * @param newOrder признак перехода с кнопки покупки
     * @param model    модель представления
     * @return имя шаблона заказа
     */
    @GetMapping("/orders/{id}")
    public Mono<String> getOrder(
            @PathVariable long id, @RequestParam(defaultValue = "false") boolean newOrder, Model model) {
        return orderService
                .getOrder(id)
                .doOnNext(order -> {
                    model.addAttribute("order", order);
                    model.addAttribute("newOrder", newOrder);
                })
                .thenReturn("order");
    }

    /**
     * Оформляет заказ из корзины.
     *
     * <p>Покупка разделена на три шага в {@link CheckoutService}: снимок корзины,
     * оплата вне транзакции, сохранение заказа. Контроллер только переводит
     * ожидаемые ошибки в редирект с сообщением; неожиданные ошибки уходят в
     * {@code GlobalExceptionHandler}.
     *
     * <p>Особенно важен случай {@code 402}: при недостатке средств заказ не
     * создаётся, а корзина остаётся нетронутой. Пользователь возвращается в
     * корзину и видит причину, а не пустую страницу с 500.
     *
     * @return имя шаблона заказа или редирект в корзину
     */
    @PostMapping("/buy")
    public Mono<String> buy() {
        return checkoutService
                .checkout()
                .map(orderId -> "redirect:/orders/" + orderId + "?newOrder=true")
                .onErrorResume(EmptyCartException.class, e -> {
                    LOG.warn("Покупка отменена: корзина пуста: {}", e.getMessage());
                    return Mono.just("redirect:/cart/items");
                })
                .onErrorResume(InsufficientFundsException.class, e -> {
                    LOG.warn("Покупка отменена: недостаточно средств");
                    return Mono.just("redirect:/cart/items?paymentError=insufficient-funds");
                })
                .onErrorResume(InvalidPaymentRequestException.class, e -> {
                    LOG.warn("Покупка отменена: сервис платежей отклонил запрос: {}", e.getMessage());
                    return Mono.just("redirect:/cart/items?paymentError=invalid-request");
                })
                .onErrorResume(PaymentsUnavailableException.class, e -> {
                    LOG.warn("Покупка отменена: сервис платежей недоступен: {}", e.getMessage());
                    return Mono.just("redirect:/cart/items?paymentError=payments-unavailable");
                });
    }
}
