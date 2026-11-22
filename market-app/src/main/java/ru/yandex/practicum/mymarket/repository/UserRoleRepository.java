package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import ru.yandex.practicum.mymarket.model.UserRole;

/**
 * Хранилище ролей пользователей.
 */
public interface UserRoleRepository extends ReactiveCrudRepository<UserRole, Long> {

    /**
     * Возвращает все роли пользователя.
     *
     * @param userId идентификатор пользователя
     * @ поток ролей пользователя
     */
    Flux<UserRole> findByUserId(Long userId);
}
