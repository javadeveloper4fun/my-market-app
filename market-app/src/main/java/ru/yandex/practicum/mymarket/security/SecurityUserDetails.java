package ru.yandex.practicum.mymarket.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ru.yandex.practicum.mymarket.model.User;

/**
 * Адапт доменного пользователя {@link User} к {@link UserDetails} Spring Security.
 *
 * <p>Пароль не раскрывается наружу: {@code getPassword()} возвращает хеш, а
 * Spring Security сравнивает его самостоятельно через {@link
 * org.springframework.security.crypto.password.PasswordEncoder}.
 */
public class SecurityUserDetails implements UserDetails {

    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;

    public SecurityUserDetails(User user, Collection<? extends GrantedAuthority> authorities) {
        this.user = user;
        this.authorities = authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    public User getUser() {
        return user;
    }
}
