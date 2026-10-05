package ru.yandex.practicum.mymarket;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Тестовый контейнер с настоящим PostgreSQL (Спринт 5, Тема 5, Урок 2).
 *
 * <p>Основные тесты работают на встроенной H2 ради скорости, но H2 в режиме
 * {@code MODE=PostgreSQL} воспроизводит поведение боевой СУБД лишь частично.
 * Тесты, которым важна настоящая семантика PostgreSQL, подключают эту
 * конфигурацию через {@code @Import} и получают реальный сервер.
 *
 * <p>Контейнер описан нестатическим {@code @Bean}-методом с аннотацией
 * {@link ServiceConnection}. Spring Boot сам запускает контейнер при создании
 * контекста, подставляет его {@code getJdbcUrl()}, имя пользователя и пароль в
 * {@code DataSource} и останавливает контейнер при закрытии контекста.
 * Поэтому {@code @DynamicPropertySource} здесь не нужен: он потребовался бы,
 * если бы параметры подключения задавались вручную.
 *
 * <p>Образ совпадает с образом боевой БД в {@code docker-compose.yml}: проверка
 * на другой версии PostgreSQL ничего не гарантировала бы для прода.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgreSqlTestContainerConfiguration {

    /**
     * Контейнер с PostgreSQL для тестов.
     *
     * @return запущенный контейнер, подключённый к DataSource автоматически
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgreSqlContainer() {
        return new PostgreSQLContainer("postgres:17.5");
    }
}
