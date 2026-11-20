package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartView;
import ru.yandex.practicum.mymarket.service.Action;
import ru.yandex.practicum.mymarket.service.CartPageService;
import ru.yandex.practicum.mymarket.service.CartService;

/**
 * Корзина покупателя: отображение и изменение количества позиций.
 *
 * <p>Контроллер возвращает {@link Mono} с именем шаблона Thymeleaf, а не готовую
 * страницу: цепочка подписывается при отправке ответа, и поток не занимается в
 * ожидании базы. Работа с корзиной перенесена из спринта 5 на реактивный стек
 * спринта 6 (WebFlux + Spring Data R2DBC).
 */
@Controller
public class CartController {

    private final CartService cartService;
    private final CartPageService cartPageService;

    public CartController(CartService cartService, CartPageService cartPageService) {
        this.cartService = cartService;
        this.cartPageService = cartPageService;
    }

    /**
     * Отдаёт страницу корзины.
     *
     * <p>Метод возвращает {@link Mono}: страница готовится реактивно, и до
     * подписки отдавать имя шаблона рано — на момент вызова корзина ещё не
     * прочитана.
     *
     * @param model        модель представления
     * @param paymentError код ошибки платежей из редиректа после покупки
     * @return имя шаблона корзины
     */
    @GetMapping("/cart/items")
    public Mono<String> getCart(
            Model model, @RequestParam(name = "paymentError", required = false) String paymentError) {
        return cartPageService
                .getCartView()
                .doOnNext(view -> fillModel(model, view, paymentError))
                .thenReturn("cart");
    }

    /**
     * Изменяет количество или удаляет товар из корзины.
     *
     * <p>Метод возвращает шаблон напрямую, а не редирект: этого требует контракт
     * спринта 5. В отличие от витрины и списка заказов здесь нет подтверждения, что
     * браузер показал результат с сервера, поэтому обновление страницы повторит
     * POST-запрос и действие применится дважды — например, {@code PLUS} увеличит
     * количество на две единицы. Отличить намеренное повторное действие от
     * повторной отправки здесь нечем: идентификатора запроса нет.
     *
     * <p>В приложении без этого ограничения здесь был бы редирект на {@code /cart/items}
     * по схеме POST-Redirect-GET, и страницу можно было бы перезагружать безопасно.
     *
     * <p>Проверка на пустой результат не нужна: {@code applyAction} завершается
     * пустым {@code Mono} и для отсутствующей позиции, и это штатный исход —
     * уменьшать или удалять нечего.
     *
     * @param id     идентификатор товара
     * @param action действие над товаром
     * @param model  модель представления
     * @return имя шаблона корзины
     */
    @PostMapping("/cart/items")
    public Mono<String> changeCartItem(@RequestParam long id, @RequestParam Action action, Model model) {
        return cartService
                .applyAction(id, action)
                .then(cartPageService.getCartView())
                .doOnNext(view -> fillModel(model, view, null))
                .thenReturn("cart");
    }

    /**
     * Наполняет модель корзины.
     *
     * @param model        модель представления
     * @param view         корзина и состояние платежей
     * @param paymentError код ошибки, пришедший редиректом после покупки
     */
    private void fillModel(Model model, CartView view, String paymentError) {
        model.addAttribute("items", view.cart().items());
        model.addAttribute("total", view.cart().total());
        model.addAttribute("balance", view.balance());
        model.addAttribute("canPay", view.canPay());
        model.addAttribute("insufficientFunds", view.insufficientFunds());
        model.addAttribute("paymentMessage", paymentMessage(view, paymentError));
    }

    /**
     * Выбирает сообщение об ошибке платежей для страницы.
     *
     * @param view         состояние корзины
     * @param paymentError код из редиректа
     * @return сообщение или {@code null}
     */
    private String paymentMessage(CartView view, String paymentError) {
        if (view.paymentError() != null) {
            return view.paymentError();
        }
        if ("insufficient-funds".equals(paymentError) && !view.insufficientFunds()) {
            // Обычно причину показывает блок insufficientFunds. Сообщение нужно
            // для гонки, когда баланс уже изменился и этот блок не отрисуется.
            return "Недостаточно средств для оплаты заказа.";
        }
        if ("invalid-request".equals(paymentError)) {
            return "Не удалось оформить заказ: сервис платежей отклонил сумму.";
        }
        if ("payments-unavailable".equals(paymentError)) {
            return "Сервис платежей недоступен. Оформление заказа временно невозможно.";
        }
        return null;
    }
}
