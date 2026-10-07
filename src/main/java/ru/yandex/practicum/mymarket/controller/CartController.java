package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartPage;
import ru.yandex.practicum.mymarket.service.Action;
import ru.yandex.practicum.mymarket.service.CartService;

@Controller
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * Отдаёт страницу корзины.
     *
     * <p>Метод возвращает {@link Mono}: страница готовится реактивно, и до
     * подписки отдавать имя шаблона рано — на момент вызова корзина ещё не
     * прочитана.
     *
     * @param model модель представления
     * @return имя шаблона корзины
     */
    @GetMapping("/cart/items")
    public Mono<String> getCart(Model model) {
        return cartService.getCart().doOnNext(cart -> fillModel(model, cart)).thenReturn("cart");
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
                .then(cartService.getCart())
                .doOnNext(cart -> fillModel(model, cart))
                .thenReturn("cart");
    }

    /**
     * Наполняет модель корзины.
     *
     * @param model модель представления
     * @param cart  содержимое корзины
     */
    private void fillModel(Model model, CartPage cart) {
        model.addAttribute("items", cart.items());
        model.addAttribute("total", cart.total());
    }
}
