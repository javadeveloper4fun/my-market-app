package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.model.User;

/**
 * Хранилище пользователей магазина.
 */
public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    /**
     * Находит пользователя по логину.
     *
     * @param username логин пользователя
     * @return пользователь или пустой результат, если пользователя нет
     */
    Mono<User> findByUsername(String username);
}
