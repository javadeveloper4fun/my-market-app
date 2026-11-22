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
import ru.yandex.practicum.mymarket.security.CurrentUserProvider;
import ru.yandex.practicum.mymarket.service.CheckoutService;
import ru.yandex.practicum.mymarket.service.EmptyCartException;
import ru.yandex.practicum.mymarket.service.OrderService;

/**
 * Заказы покупателя: список заказов, карточка заказа и оформление.
 *
 * <p>Все операции выполняются в контексте текущего авторизованного пользователя.
 * Оформление заказа ({@code /buy}) делегируется {@link CheckoutService}.
 */
@Controller
public class OrderController {

    private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final CheckoutService checkoutService;
    private final CurrentUserProvider currentUserProvider;

    public OrderController(
            OrderService orderService, CheckoutService checkoutService, CurrentUserProvider currentUserProvider) {
        this.orderService = orderService;
        this.checkoutService = checkoutService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Отдаёт список заказов.
     *
     * @param model модель представления
     * @return имя шаблона заказов
     */
    @GetMapping("/orders")
    public Mono<String> getOrders(Model model) {
        return currentUserProvider
                .getCurrentUser()
                .flatMap(user ->
                        orderService.getOrders(user.getId()).doOnNext(orders -> model.addAttribute("orders", orders)))
                .thenReturn("orders");
    }

    /**
     * Отдаёт страницу заказа.
     *
     * @param id идентификатор заказа
     * @param newOrder признак нового заказа
     * @param model модель представления
     * @return имя шаблона заказа
     */
    @GetMapping("/orders/{id}")
    public Mono<String> getOrder(
            @PathVariable long id, @RequestParam(defaultValue = "false") boolean newOrder, Model model) {
        return currentUserProvider
                .getCurrentUser()
                .flatMap(user -> orderService.getOrder(id, user.getId()).doOnNext(order -> {
                    model.addAttribute("order", order);
                    model.addAttribute("newOrder", newOrder);
                }))
                .thenReturn("order");
    }

    /**
     * Оформляет покупку.
     *
     * @return редирект на заказ или корзину
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
