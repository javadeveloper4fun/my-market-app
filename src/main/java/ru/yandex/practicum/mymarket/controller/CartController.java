package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * Отображает страницу корзины.
     *
     * @param model модель представления
     * @return имя шаблона корзины
     */
    @GetMapping("/cart/items")
    public String getCart(Model model) {
        CartPage cart = cartService.getCart();
        model.addAttribute("items", cart.items());
        model.addAttribute("total", cart.total());
        return "cart";
    }

    /**
     * Изменяет количество или удаляет товар из корзины.
     *
     * @param id     идентификатор товара
     * @param action действие над товаром
     * @param model  модель представления
     * @return имя шаблона корзины
     */
    @PostMapping("/cart/items")
    public String changeCartItem(@RequestParam long id, @RequestParam Action action, Model model) {
        cartService.applyAction(id, action);
        CartPage cart = cartService.getCart();
        model.addAttribute("items", cart.items());
        model.addAttribute("total", cart.total());
        return "cart";
    }
}
