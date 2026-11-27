package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartView;
import ru.yandex.practicum.mymarket.security.CurrentUserProvider;
import ru.yandex.practicum.mymarket.service.Action;
import ru.yandex.practicum.mymarket.service.CartPageService;
import ru.yandex.practicum.mymarket.service.CartService;

/**
 * Корзина покупателя: отображение и изменение количества позиций.
 *
 * <p>Все операции выполняются в контексте текущего авторизованного пользователя.
 */
@Controller
public class CartController {

    private final CartService cartService;
    private final CartPageService cartPageService;
    private final CurrentUserProvider currentUserProvider;

    public CartController(
            CartService cartService, CartPageService cartPageService, CurrentUserProvider currentUserProvider) {
        this.cartService = cartService;
        this.cartPageService = cartPageService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Отдаёт страницу корзины.
     *
     * @param model модель представления
     * @param paymentError код ошибки оплаты
     * @param attemptId    идентификатор попытки после таймаута
     * @return имя шаблона корзины
     */
    @GetMapping("/cart/items")
    public Mono<String> getCart(
            Model model,
            @RequestParam(name = "paymentError", required = false) String paymentError,
            @RequestParam(name = "attemptId", required = false) String attemptId) {
        return cartPageService
                .getCartView()
                .doOnNext(view -> fillModel(model, view, paymentError, attemptId))
                .thenReturn("cart");
    }

    /**
     * Изменяет товар в корзине.
     *
     * @param id идентификатор товара
     * @param action действие
     * @param model модель представления
     * @return имя шаблона корзины
     */
    @PostMapping("/cart/items")
    public Mono<String> changeCartItem(@RequestParam long id, @RequestParam Action action, Model model) {
        return currentUserProvider
                .getCurrentUser()
                .flatMap(user -> cartService.applyAction(id, action, user.getId()))
                .then(cartPageService.getCartView())
                .doOnNext(view -> fillModel(model, view, null, null))
                .thenReturn("cart");
    }

    private void fillModel(Model model, CartView view, String paymentError, String attemptId) {
        model.addAttribute("items", view.cart().items());
        model.addAttribute("total", view.cart().total());
        model.addAttribute("balance", view.balance());
        model.addAttribute("canPay", view.canPay());
        model.addAttribute("insufficientFunds", view.insufficientFunds());
        model.addAttribute("attemptId", attemptId);
        model.addAttribute("paymentMessage", paymentMessage(view, paymentError));
    }

    private String paymentMessage(CartView view, String paymentError) {
        if (view.paymentError() != null) {
            return view.paymentError();
        }
        if ("insufficient-funds".equals(paymentError) && !view.insufficientFunds()) {
            return "Недостаточно средств для оплаты заказа.";
        }
        if ("invalid-request".equals(paymentError)) {
            return "Не удалось оформить заказ: сервис платежей отклонил сумму.";
        }
        if ("payments-unavailable".equals(paymentError)) {
            return "Сервис платежей недоступен. Оформление заказа временно невозможно.";
        }
        if ("attempt-conflict".equals(paymentError)) {
            return "Другая покупка уже оформляется. Повторите попытку позже.";
        }
        return null;
    }
}
