package ru.yandex.practicum.mymarket.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.User;

/**
 * Извлечение текущего авторизованного пользователя из контекста безопасности WebFlux.
 *
 * <p>Используется в сервисах корзины, заказов и оформления покупки для привязки
 * операций к пользователю. Если пользователь не аутентифицирован, возвращает
 * пустой {@link Mono}.
 */
@Component
public class CurrentUserProvider {

    /**
     * Возвращает текущего пользователя или пустой результат, если пользователь не вошёл.
     *
     * @return пользователь из SecurityContext или пустой Mono
     */
    public Mono<User> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated())
                .cast(Authentication.class)
                .map(auth -> (SecurityUserDetails) auth.getPrincipal())
                .map(SecurityUserDetails::getUser);
    }
}
