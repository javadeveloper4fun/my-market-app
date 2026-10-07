package ru.yandex.practicum.mymarket.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Бин {@link DataSource} исключительно для Liquibase.
 *
 * <p>Без него миграции не выполняются, и это происходит молча. В Spring Boot 4
 * {@code DataSourceAutoConfiguration} помечена
 * {@code @ConditionalOnMissingBean(type = "io.r2dbc.spi.ConnectionFactory")}:
 * стоит появиться бину R2DBC, и автоконфигурация JDBC отключается. При этом
 * {@code LiquibaseAutoConfiguration} требует бин {@code DataSource} и, не найдя
 * его, просто не создаёт {@code SpringLiquibase}. Приложение стартует, запросы
 * идут, а таблиц нет — падает первая же выборка с
 * {@code relation "items" does not exist}.
 *
 * <p>Объявление бина вручную снимает проблему: условие
 * {@code LiquibaseDataSourceCondition} проверяет наличие бина {@code DataSource}
 * по типу, а не по имени, поэтому миграции начинают выполняться на старте.
 *
 * <p>Данные подключения берутся из {@link JdbcConnectionDetails}, если такой бин
 * есть, и только затем — из свойств {@code spring.datasource.*}. Порядок не
 * переставлен случайно. В тестах с Testcontainers адрес базы приходит не из
 * свойств, а из {@code @ServiceConnection}, который публикует именно
 * {@code JdbcConnectionDetails}. Если читать свойства в первую очередь, то
 * {@code determineUrl()} упрётся в пустое значение и миграции не запустятся
 * вовсе — то есть ровно тот отказ, ради которого этот класс и написан.
 *
 * <p>Пул соединений здесь не нужен и был бы лишним. Единственный потребитель —
 * Liquibase, который открывает соединение один раз на весь прогон миграций и
 * закрывает его. Создание пула означало бы держать соединения открытыми ради
 * единственного использования на старте.
 */
@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
public class LiquibaseDataSourceConfig {

    /**
     * Создаёт DataSource для миграций.
     *
     * <p>Имя бина совпадает с обычным — иначе часть автоконфигураций, ищущих
     * бины по имени, повела бы себя иначе, чем в блокирующей версии.
     *
     * @param connectionDetails детали подключения от {@code @ServiceConnection}
     * @param properties        свойства подключения из {@code spring.datasource.*}
     * @return DataSource без пула, только для Liquibase
     */
    @Bean
    public DataSource dataSource(
            ObjectProvider<JdbcConnectionDetails> connectionDetails, DataSourceProperties properties) {
        JdbcConnectionDetails details = connectionDetails.getIfAvailable();
        if (details != null) {
            return fromDetails(details);
        }
        return fromProperties(properties);
    }

    /**
     * Собирает DataSource по данным, опубликованным {@code @ServiceConnection}.
     *
     * @param details детали подключения
     * @return DataSource для миграций
     */
    private DataSource fromDetails(JdbcConnectionDetails details) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(details.getDriverClassName());
        dataSource.setUrl(details.getJdbcUrl());
        dataSource.setUsername(details.getUsername());
        dataSource.setPassword(details.getPassword());
        return dataSource;
    }

    /**
     * Собирает DataSource по свойствам {@code spring.datasource.*}.
     *
     * @param properties свойства подключения
     * @return DataSource для миграций
     */
    private DataSource fromProperties(DataSourceProperties properties) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(properties.determineDriverClassName());
        dataSource.setUrl(properties.determineUrl());
        dataSource.setUsername(properties.determineUsername());
        dataSource.setPassword(properties.determinePassword());
        return dataSource;
    }
}
