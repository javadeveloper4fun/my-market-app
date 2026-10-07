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
import ru.yandex.practicum.mymarket.service.EmptyCartException;
import ru.yandex.practicum.mymarket.service.OrderService;

@Controller
public class OrderController {

    private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
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
     * <p>Транзакция открыта в {@code OrderService.buy()}, а не здесь: перехват
     * ошибки происходит уже после её закрытия, поэтому откат уже не отменить.
     *
     * <p>Перехватывается только {@link EmptyCartException}: пустая корзина —
     * ожидаемое состояние, а не сбой. Любая другая ошибка пробрасывается в
     * {@code GlobalExceptionHandler}, а не растворяется в редиректе.
     *
     * <p>Перехват устроен через {@code onErrorResume}, а не через
     * {@code try-catch}. {@code EmptyCartException} доходит сюда не выброшенным
     * на стеке вызова, а сигналом ошибки в {@code Mono}: цепочка
     * {@code buy()} выполняется при подписке, уже после возврата из метода, и к
     * этому моменту обычный {@code catch} в методе был бы бесполезен.
     *
     * @return имя шаблона заказа или редирект в корзину
     */
    @PostMapping("/buy")
    public Mono<String> buy() {
        return orderService
                .buy()
                .map(orderId -> "redirect:/orders/" + orderId + "?newOrder=true")
                .onErrorResume(EmptyCartException.class, e -> {
                    LOG.warn("Покупка отменена: корзина пуста: {}", e.getMessage());
                    return Mono.just("redirect:/cart/items");
                });
    }
}
