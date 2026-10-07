package ru.yandex.practicum.mymarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;

/**
 * Главный класс Spring Boot-приложения «Витрина интернет-магазина».
 * {@code @SpringBootApplication} включает автоматическую настройку,
 * сканирование компонентов и конфигурацию приложения.
 *
 * <p>Спринт 6 — реактивный стек: WebFlux и Spring Data R2DBC. Приложение
 * запускается на Netty, а не в сервлетном контейнере, и упаковывается в
 * Executable Jar. Прежний блокирующий стек (Spring Web MVC + Spring Data JPA)
 * больше не используется.
 *
 * <p>Здесь же исключена автоконфигурация
 * {@link DataSourceTransactionManagerAutoConfiguration}. Исключение вызвано
 * тем, что приложению нужен {@code DataSource} только ради Liquibase: см.
 * {@link ru.yandex.practicum.mymarket.config.LiquibaseDataSourceConfig}, где
 * он объявлен вручную, потому что автоконфигурация JDBC отключается при
 * наличии бина R2DBC. Как только бин {@code DataSource} появляется, эта
 * автоконфигурация начинает создавать JDBC-менеджер транзакций поверх
 * R2DBC-менеджера, и их становится два — выбор менеджера для
 * {@code @Transactional} становится неоднозначным, и запросы падают с
 * {@code NoUniqueBeanDefinitionException}. Транзакции в приложении
 * обслуживает {@code R2dbcTransactionManager}, и JDBC-менеджер здесь только
 * мешает.
 */
@SpringBootApplication(exclude = DataSourceTransactionManagerAutoConfiguration.class)
public class MyMarketAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMarketAppApplication.class, args);
    }
}
