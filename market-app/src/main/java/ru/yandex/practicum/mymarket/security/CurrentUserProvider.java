package ru.yandex.practicum.mymarket.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.User;
import ru.yandex.practicum.mymarket.repository.UserRepository;

/**
 * Получение текущего аутентифицированного пользователя из контекста безопасности WebFlux.
 */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Возвращает текущего пользователя или пустой результат, если пользователь не найден.
     *
     * @return пользователь из SecurityContext или пустой Mono
     */
    public Mono<User> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated())
                .cast(Authentication.class)
                .flatMap(auth -> {
                    Object principal = auth.getPrincipal();
                    if (principal instanceof SecurityUserDetails sud) {
                        return Mono.just(sud.getUser());
                    }
                    String username = principal instanceof UserDetails ud ? ud.getUsername() : auth.getName();
                    if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
                        return Mono.empty();
                    }
                    return userRepository.findByUsername(username);
                });
    }
}
