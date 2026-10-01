package ru.yandex.practicum.mymarket.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.service.OrderService;

/**
 * Слайсовые MockMvc-тесты контроллера заказов.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    /**
     * Проверяет, что GET /orders рендерит список заказов.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void getOrdersRendersOrdersView() throws Exception {
        OrderDto order = new OrderDto(1L, List.of(), 7000L);
        when(orderService.getOrders()).thenReturn(List.of(order));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(model().attributeExists("orders"));
    }

    /**
     * Проверяет, что GET /orders/{id} рендерит заказ с флагом newOrder=true.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void getOrderRendersOrderViewWithNewOrderFlag() throws Exception {
        ItemDto item = new ItemDto(1L, "Куртка", "", "", 1500L, 2);
        OrderDto order = new OrderDto(1L, List.of(item), 3000L);
        when(orderService.getOrder(eq(1L))).thenReturn(order);

        mockMvc.perform(get("/orders/1").param("newOrder", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("order"))
                .andExpect(model().attributeExists("order"))
                .andExpect(model().attribute("newOrder", true));
    }

    /**
     * Проверяет, что по умолчанию флаг newOrder равен false.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void getOrderDefaultsNewOrderToFalse() throws Exception {
        OrderDto order = new OrderDto(1L, List.of(), 0L);
        when(orderService.getOrder(eq(1L))).thenReturn(order);

        mockMvc.perform(get("/orders/1")).andExpect(status().isOk()).andExpect(model().attribute("newOrder", false));
    }

    /**
     * Проверяет, что POST /buy редиректит на страницу нового заказа.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void buyRedirectsToNewOrder() throws Exception {
        when(orderService.buy()).thenReturn(42L);

        mockMvc.perform(post("/buy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/42?newOrder=true"));
    }

    /**
     * Проверяет, что POST /buy при пустой корзине редиректит в корзину.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void buyRedirectsToCartWhenCartIsEmpty() throws Exception {
        when(orderService.buy()).thenThrow(new IllegalStateException("Cart is empty"));

        mockMvc.perform(post("/buy")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cart/items"));
    }

    /**
     * Проверяет, что несуществующий заказ даёт 404 (а не 500).
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void getOrderReturns404WhenNotFound() throws Exception {
        when(orderService.getOrder(eq(999L))).thenThrow(new IllegalArgumentException("Order not found: 999"));

        mockMvc.perform(get("/orders/999")).andExpect(status().isNotFound());
    }
}
