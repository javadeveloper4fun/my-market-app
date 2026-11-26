package ru.yandex.practicum.mymarket.repository;

import io.r2dbc.spi.ConnectionFactory;
import java.util.Locale;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

class CartItemRepositoryImpl implements CartItemRepositoryCustom {

    private final DatabaseClient databaseClient;
    private final boolean postgres;

    CartItemRepositoryImpl(DatabaseClient databaseClient, ConnectionFactory connectionFactory) {
        this.databaseClient = databaseClient;
        String name = connectionFactory.getMetadata().getName();
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (normalized.contains("postgres")) {
            this.postgres = true;
        } else if (normalized.contains("h2")) {
            this.postgres = false;
        } else {
            throw new IllegalStateException("Неизвестный диалект БД для UPSERT корзины: " + name);
        }
    }

    @Override
    public Mono<Integer> upsertIncreaseCount(long itemId, Long userId) {
        String sql = postgres ? postgresSql() : h2Sql();
        return databaseClient
                .sql(sql)
                .bind("itemId", itemId)
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .map(Long::intValue);
    }

    private String postgresSql() {
        return """
                INSERT INTO cart_item (item_id, user_id, count)
                VALUES (:itemId, :userId, 1)
                ON CONFLICT (item_id, user_id)
                DO UPDATE SET count = cart_item.count + 1
                """;
    }

    private String h2Sql() {
        return """
                MERGE INTO cart_item AS target
                USING (
                        SELECT CAST(:itemId AS BIGINT) AS item_id,
                               CAST(:userId AS BIGINT) AS user_id,
                               1 AS count
                ) AS source
                ON target.item_id = source.item_id AND target.user_id = source.user_id
                WHEN MATCHED THEN
                    UPDATE SET count = target.count + 1
                WHEN NOT MATCHED THEN
                    INSERT (item_id, user_id, count)
                    VALUES (source.item_id, source.user_id, source.count)
                """;
    }
}
