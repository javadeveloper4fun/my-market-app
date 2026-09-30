package ru.yandex.practicum.mymarket.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mymarket.dto.CartPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.Action;
import ru.yandex.practicum.mymarket.service.CartService;

/**
 * Слайсовые MockMvc-тесты контроллера корзины.
 */
@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    /**
     * Проверяет, что GET /cart/items рендерит корзину с атрибутами items и total.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void getCartRendersCartView() throws Exception {
        ItemDto item = new ItemDto(1L, "Куртка", "Описание", "images/jacket.jpg", 4500L, 2);
        CartPage cart = new CartPage(List.of(item), 9000L);
        when(cartService.getCart()).thenReturn(cart);

        mockMvc.perform(get("/cart/items"))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(model().attributeExists("items", "total"))
                .andExpect(model().attribute("total", 9000L));
    }

    /**
     * Проверяет, что POST /cart/items рендерит корзину (по ТЗ) после изменения.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void postCartRendersCartView() throws Exception {
        CartPage cart = new CartPage(List.of(), 0L);
        when(cartService.getCart()).thenReturn(cart);

        mockMvc.perform(post("/cart/items").param("id", "1").param("action", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(model().attributeExists("items", "total"));

        verify(cartService).applyAction(eq(1L), eq(Action.DELETE));
    }

    /**
     * Проверяет, что невалидный action даёт 400 (а не 500).
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void postCartRejectsInvalidAction() throws Exception {
        mockMvc.perform(post("/cart/items").param("id", "1").param("action", "BOGUS"))
                .andExpect(status().isBadRequest());
    }
}
