package ru.yandex.practicum.mymarket.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.service.Action;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;

/**
 * Слайсовые MockMvc-тесты контроллера витрины товаров.
 */
@WebMvcTest(ItemsController.class)
class ItemsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ItemService itemService;

    @MockitoBean
    private CartService cartService;

    /**
     * Проверяет, что GET / рендерит витрину с атрибутами модели из сервиса.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void getItemsRendersItemsView() throws Exception {
        ItemsPage page = new ItemsPage(List.of(), "", "NO", new Paging(5, 1, false, false));
        when(itemService.getItems(eq(""), eq("NO"), eq(1), eq(5))).thenReturn(page);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("items"))
                .andExpect(model().attributeExists("items", "search", "sort", "paging"));
    }

    /**
     * Проверяет, что GET /items передаёт параметры запроса в сервис без изменений.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void getItemsPassesRequestParamsToService() throws Exception {
        ItemDto dto = new ItemDto(1L, "Куртка", "Описание", "images/jacket.jpg", 4500L, 0);
        ItemsPage page = ItemsPage.of(List.of(dto), "куртка", "ALPHA", new Paging(5, 1, false, false));
        when(itemService.getItems(eq("куртка"), eq("ALPHA"), eq(1), eq(5))).thenReturn(page);

        mockMvc.perform(get("/items").param("search", "куртка").param("sort", "ALPHA"))
                .andExpect(status().isOk())
                .andExpect(view().name("items"))
                .andExpect(model().attribute("items", page.rows()))
                .andExpect(model().attribute("search", "куртка"))
                .andExpect(model().attribute("sort", "ALPHA"));
    }

    /**
     * Проверяет, что POST /items редиректит на витрину, сохраняя параметры запроса.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void postItemsRedirectsWithPreservedParams() throws Exception {
        mockMvc.perform(post("/items")
                        .param("id", "1")
                        .param("action", "PLUS")
                        .param("search", "куртка")
                        .param("sort", "ALPHA")
                        .param("pageNumber", "2")
                        .param("pageSize", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/items?search=%D0%BA%D1%83%D1%80%D1%82%D0%BA%D0%B0&sort=ALPHA&pageNumber=2&pageSize=10"));

        verify(cartService).applyAction(eq(1L), eq(Action.PLUS));
    }

    /**
     * Проверяет, что GET /items/{id} рендерит карточку товара.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void getItemRendersItemView() throws Exception {
        ItemDto dto = new ItemDto(1L, "Куртка", "Описание", "images/jacket.jpg", 4500L, 0);
        when(itemService.getItem(eq(1L))).thenReturn(dto);

        mockMvc.perform(get("/items/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("item"))
                .andExpect(model().attributeExists("item"));
    }

    /**
     * Проверяет, что POST /items/{id} рендерит карточку товара (по ТЗ) после изменения.
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void postItemRendersItemView() throws Exception {
        ItemDto dto = new ItemDto(1L, "Куртка", "Описание", "images/jacket.jpg", 4500L, 1);
        when(itemService.getItem(eq(1L))).thenReturn(dto);

        mockMvc.perform(post("/items/1").param("action", "MINUS"))
                .andExpect(status().isOk())
                .andExpect(view().name("item"))
                .andExpect(model().attributeExists("item"));

        verify(cartService).applyAction(eq(1L), eq(Action.MINUS));
    }

    /**
     * Проверяет, что невалидный action даёт 400 (а не 500).
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void postItemRejectsInvalidAction() throws Exception {
        mockMvc.perform(post("/items/1").param("action", "UNKNOWN")).andExpect(status().isBadRequest());
    }

    /**
     * Проверяет, что карточка несуществующего товара даёт 404 (а не 500).
     *
     * @throws Exception при ошибке выполнения запроса
     */
    @Test
    void getItemReturns404WhenNotFound() throws Exception {
        when(itemService.getItem(eq(999L))).thenThrow(new IllegalArgumentException("Item not found: 999"));

        mockMvc.perform(get("/items/999")).andExpect(status().isNotFound());
    }
}
