package ru.yandex.practicum.mymarket.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Роль пользователя: привязка user_id -> роль (например, ROLE_USER).
 */
@Table("user_roles")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class UserRole {

    @Id
    private Long id;

    private Long userId;

    private String role;

    public UserRole(Long userId, String role) {
        this.userId = userId;
        this.role = role;
    }
}
