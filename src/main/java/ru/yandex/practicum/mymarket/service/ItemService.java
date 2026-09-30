package ru.yandex.practicum.mymarket.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsPage;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

@Service
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final CartItemRepository cartItemRepository;

    public ItemService(ItemRepository itemRepository, CartItemRepository cartItemRepository) {
        this.itemRepository = itemRepository;
        this.cartItemRepository = cartItemRepository;
    }

    public ItemsPage getItems(String search, String sort, int pageNumber, int pageSize) {
        String searchForView = (search != null) ? search.trim() : "";
        String querySearch = !searchForView.isBlank() ? escapeLike(searchForView) : null;
        String normalizedSort = (sort == null || sort.isBlank()) ? "NO" : sort;
        Sort jpaSort =
                switch (normalizedSort) {
                    case "ALPHA" -> Sort.by("title");
                    case "PRICE" -> Sort.by("price");
                    default -> Sort.unsorted();
                };
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize, jpaSort);
        Page<Item> page = (querySearch != null)
                ? itemRepository.searchByText(querySearch, pageRequest)
                : itemRepository.findAll(pageRequest);

        List<CartItem> cartItems = cartItemRepository.findAllWithItem();
        Map<Long, Integer> cartCountByItemId =
                cartItems.stream().collect(Collectors.toMap(ci -> ci.getItem().getId(), CartItem::getCount));

        List<ItemDto> items = page.getContent().stream()
                .map(item -> ItemDto.fromEntity(item, cartCountByItemId.getOrDefault(item.getId(), 0)))
                .toList();

        Paging paging = Paging.from(page);
        return ItemsPage.of(items, searchForView, normalizedSort, paging);
    }

    public ItemDto getItem(long id) {
        Item item =
                itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Item not found: " + id));
        int count = cartItemRepository.findByItemId(id).map(CartItem::getCount).orElse(0);
        return ItemDto.fromEntity(item, count);
    }

    /**
     * Экранирует спецсимволы LIKE (%, _, \), чтобы они обрабатывались как литералы.
     *
     * @param text исходный текст поиска
     * @return текст с экранированными спецсимволами
     */
    private String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
