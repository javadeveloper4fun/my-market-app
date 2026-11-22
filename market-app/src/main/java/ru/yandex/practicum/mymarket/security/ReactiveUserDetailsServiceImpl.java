package ru.yandex.practicum.mymarket.security;

import java.util.stream.Collectors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.UserRole;
import ru.yandex.practicum.mymarket.repository.UserRepository;
import ru.yandex.practicum.mymarket.repository.UserRoleRepository;

/**
 * Загрузка пользователя и его ролей из БД для аутентификации Spring Security.
 *
 * <p>Реализует {@link ReactiveUserDetailsService} для реактивного стека
 * WebFlux: {@code findByUsername} возвращает {@code Mono<UserDetails>}.
 */
@Service
public class ReactiveUserDetailsServiceImpl implements ReactiveUserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public ReactiveUserDetailsServiceImpl(UserRepository userRepository,
                                          UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return userRepository
                .findByUsername(username)
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(
                        "Пользователь с логином " + username + " не найден")))
                .flatMap(user -> userRoleRepository
                        .findByUserId(user.getId())
                        .map(UserRole::getRole)
                        .map(role -> new SimpleGrantedAuthority(role))
                        .collect(Collectors.toList())
                        .map(authorities -> new SecurityUserDetails(user, authorities)));
    }
}
