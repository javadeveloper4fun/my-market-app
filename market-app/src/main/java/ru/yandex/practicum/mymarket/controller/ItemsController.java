package ru.yandex.practicum.mymarket.controller;

import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.InvalidItemFormException;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.NewItemForm;
import ru.yandex.practicum.mymarket.security.CurrentUserProvider;
import ru.yandex.practicum.mymarket.service.Action;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ImageUploadException;
import ru.yandex.practicum.mymarket.service.ItemService;

/**
 * Контроллер витрины товаров.
 */
@Controller
public class ItemsController {

    private final ItemService itemService;
    private final CartService cartService;
    private final CurrentUserProvider currentUserProvider;

    public ItemsController(ItemService itemService, CartService cartService, CurrentUserProvider currentUserProvider) {
        this.itemService = itemService;
        this.cartService = cartService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping({"/", "/items"})
    public Mono<String> getItems(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "NO") String sort,
            Model model) {
        return currentUserProvider
                .getCurrentUser()
                .map(user -> java.util.Optional.of(user.getId()))
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(userId -> itemService
                        .getItems(search, sort, pageNumber, pageSize, userId.orElse(null))
                        .doOnNext(page -> {
                            model.addAttribute("items", page.rows());
                            model.addAttribute("search", page.search());
                            model.addAttribute("sort", page.sort());
                            model.addAttribute("paging", page.paging());
                            model.addAttribute("placeholderId", ItemDto.PLACEHOLDER_ID);
                        }))
                .thenReturn("items");
    }

    @PostMapping("/items")
    public Mono<String> changeItemCount(
            @RequestParam long id,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "NO") String sort,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam Action action) {

        String url = UriComponentsBuilder.fromPath("/items")
                .queryParam("search", search)
                .queryParam("sort", sort)
                .queryParam("pageNumber", pageNumber)
                .queryParam("pageSize", pageSize)
                .build()
                .toUriString();

        return currentUserProvider
                .getCurrentUser()
                .flatMap(user -> cartService.applyAction(id, action, user.getId()))
                .thenReturn("redirect:" + url);
    }

    @GetMapping("/items/new")
    public Mono<String> getNewItemForm() {
        return Mono.just("item-new");
    }

    @PostMapping("/items/new")
    public Mono<String> createItem(
            @ModelAttribute NewItemForm form, @RequestPart("image") FilePart image, Model model) {
        return Mono.fromRunnable(form::validate)
                .then(itemService.createItem(form.getTitle(), form.getDescription(), form.getPrice(), image))
                .thenReturn("redirect:/items")
                .onErrorResume(InvalidItemFormException.class, e -> renderNewItemForm(form, e.getMessage(), model))
                .onErrorResume(ImageUploadException.class, e -> renderNewItemForm(form, e.getMessage(), model));
    }

    @GetMapping("/items/{id}")
    public Mono<String> getItem(@PathVariable long id, Model model) {
        return currentUserProvider
                .getCurrentUser()
                .map(user -> java.util.Optional.of(user.getId()))
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(userId ->
                        itemService.getItem(id, userId.orElse(null)).doOnNext(item -> model.addAttribute("item", item)))
                .thenReturn("item");
    }

    @PostMapping("/items/{id}")
    public Mono<String> changeItemCountOnItemPage(@PathVariable long id, @RequestParam Action action, Model model) {
        return currentUserProvider
                .getCurrentUser()
                .flatMap(user -> cartService
                        .applyAction(id, action, user.getId())
                        .then(itemService.getItem(id, user.getId()))
                        .doOnNext(item -> model.addAttribute("item", item)))
                .thenReturn("item");
    }

    private Mono<String> renderNewItemForm(NewItemForm form, String error, Model model) {
        model.addAttribute("error", error);
        model.addAttribute("title", form.getTitle());
        model.addAttribute("description", form.getDescription());
        model.addAttribute("price", form.getPrice());
        return Mono.just("item-new");
    }
}
