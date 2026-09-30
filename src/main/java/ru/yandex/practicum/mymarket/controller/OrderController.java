package ru.yandex.practicum.mymarket.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.service.OrderService;

@Controller
public class OrderController {

    private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Отображает список всех заказов.
     *
     * @param model модель представления
     * @return имя шаблона списка заказов
     */
    @GetMapping("/orders")
    public String getOrders(Model model) {
        model.addAttribute("orders", orderService.getOrders());
        return "orders";
    }

    /**
     * Отображает страницу конкретного заказа.
     *
     * @param id       идентификатор заказа
     * @param newOrder флаг того, что заказ только что был совершён
     * @param model    модель представления
     * @return имя шаблона заказа
     */
    @GetMapping("/orders/{id}")
    public String getOrder(@PathVariable long id, @RequestParam(defaultValue = "false") boolean newOrder, Model model) {
        OrderDto orderDto = orderService.getOrder(id);
        model.addAttribute("order", orderDto);
        model.addAttribute("newOrder", newOrder);
        return "order";
    }

    /**
     * Оформляет заказ из текущей корзины.
     *
     * @return редирект на страницу нового заказа или в корзину (если она пуста)
     */
    @PostMapping("/buy")
    public String buy() {
        try {
            long orderId = orderService.buy();
            return "redirect:/orders/" + orderId + "?newOrder=true";
        } catch (IllegalStateException e) {
            LOG.warn("Попытка оформления заказа с пустой корзиной: {}", e.getMessage());
            return "redirect:/cart/items";
        }
    }
}
