package ru.yandex.practicum.mymarket.service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.dto.CartPage;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

@Service
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ItemRepository itemRepository;

    public CartService(CartItemRepository cartItemRepository, ItemRepository itemRepository) {
        this.cartItemRepository = cartItemRepository;
        this.itemRepository = itemRepository;
    }

    public CartPage getCart() {
        List<CartItem> cartItems = cartItemRepository.findAllWithItem();
        List<ItemDto> items = cartItems.stream()
                .map(ci -> ItemDto.fromEntity(ci.getItem(), ci.getCount()))
                .toList();
        long total = items.stream().mapToLong(i -> i.price() * i.count()).sum();
        return new CartPage(items, total);
    }

    /**
     * Применяет действие (увеличить/уменьшить/удалить) к позиции товара в корзине.
     * Если товара ещё нет в корзине и действие — PLUS, позиция создаётся с количеством 1.
     * При MINUS до нуля позиция удаляется.
     *
     * @param itemId идентификатор товара
     * @param action действие над позицией
     */
    @Transactional
    public void applyAction(long itemId, Action action) {
        Optional<CartItem> cartItemOpt = cartItemRepository.findByItemId(itemId);

        switch (action) {
            case PLUS -> {
                if (cartItemOpt.isPresent()) {
                    CartItem ci = cartItemOpt.get();
                    ci.setCount(ci.getCount() + 1);
                } else {
                    Item item = itemRepository
                            .findById(itemId)
                            .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
                    cartItemRepository.save(new CartItem(item, 1));
                }
            }
            case MINUS -> {
                if (cartItemOpt.isPresent()) {
                    CartItem ci = cartItemOpt.get();
                    if (ci.getCount() <= 1) {
                        cartItemRepository.delete(ci);
                    } else {
                        ci.setCount(ci.getCount() - 1);
                    }
                }
            }
            case DELETE -> {
                cartItemOpt.ifPresent(cartItemRepository::delete);
            }
        }
    }
}
