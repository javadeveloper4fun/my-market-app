package ru.yandex.practicum.mymarket.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Пользователь магазина: логин, хеш пароля, признак активности.
 *
 * <p>Пароль хранит только в зашифрованном виде (BCrypt). Сущность не знает о
 * Spring Security — это доменная модель, а {@link
 * ru.yandex.practicum.mymarket.security.SecurityUserDetails} адаптирует её к
 * {@link org.springframework.security.core.userdetails.UserDetails}.
 */
@Table("users")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class User {

    @Id
    private Long id;

    private String username;

    private String passwordHash;

    private boolean enabled;

    public User(String username, String passwordHash, boolean enabled) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }
}
