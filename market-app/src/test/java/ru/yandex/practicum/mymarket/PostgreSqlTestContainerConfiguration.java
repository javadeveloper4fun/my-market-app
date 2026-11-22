package ru.yandex.practicum.mymarket;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
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
 * <p>Контейнер один, а источников подключения два: приложению нужен R2DBC, а
 * Liquibase — JDBC. Контейнер помечен {@link ServiceConnection}, и для JDBC это
 * работает само: Boot читает сведения о подключении из его методов.
 *
 * <p>Для R2DBC фабрика соединений собирается вручную, и это вынужденная мера.
 * Ожидавшийся путь — объявить рядом {@code PostgreSQLR2DBCDatabaseContainer},
 * который реализует интерфейс {@code R2DBCDatabaseContainer} и отдаёт сведения
 * о подключении через R2DBC. Он лежит в модуле {@code testcontainers-r2dbc},
 * версия которого, управляемая {@code testcontainers-bom}, не публикуется:
 * сборка падает с «Could not resolve». Без этого интерфейса автоконфигурация
 * R2DBC не может обратиться к контейнеру вовсе, и контекст с настоящим
 * PostgreSQL не поднимается: {@code NoClassDefFoundError} на
 * {@code org.testcontainers.r2dbc.R2DBCDatabaseContainer}. Ручная сборка фабрики
 * из тех же данных контейнера даёт то же подключение и не зависит от
 * недоступного артефакта.
 *
 * <p>Определение фабрики здесь опережает автоконфигурацию R2DBC, поэтому Boot
 * отступает и не пытается строить фабрику сам.
 *
 * <p>На {@code DataSource} для Liquibase автоконфигурация Spring Boot
 * рассчитывать нельзя: в Boot 4 она отключается при наличии бина R2DBC, и
 * миграции молча не выполнились бы. Поэтому бин объявляется вручную в
 * {@link ru.yandex.practicum.mymarket.config.LiquibaseDataSourceConfig}, который
 * берёт параметры из сведений подключения.
 *
 * <p>Образ совпадает с образом боевой БД в {@code docker-compose.yml}: проверка
 * на другой версии PostgreSQL ничего не гарантировала бы для прода.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgreSqlTestContainerConfiguration {

    /**
     * Контейнер с PostgreSQL: он же источник сведений о подключении через JDBC.
     *
     * @return запущенный контейнер с PostgreSQL
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgreSqlContainer() {
        return new PostgreSQLContainer("postgres:17.5");
    }

    /**
     * Собирает фабрику соединений R2DBC по данным контейнера.
     *
     * @param container контейнер с PostgreSQL
     * @return фабрика соединений к контейнеру
     */
    @Bean
    ConnectionFactory testConnectionFactory(PostgreSQLContainer container) {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, container.getHost())
                .option(ConnectionFactoryOptions.PORT, container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .option(ConnectionFactoryOptions.DATABASE, container.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, container.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, container.getPassword())
                .build());
    }
}
