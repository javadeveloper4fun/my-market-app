package ru.yandex.practicum.mymarket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.mymarket.PostgreSqlTestContainerConfiguration;
import ru.yandex.practicum.mymarket.config.LiquibaseDataSourceConfig;
import ru.yandex.practicum.mymarket.model.CheckoutAttempt;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;

/**
 * Проверка репозитория попыток оформления заказа на настоящем PostgreSQL.
 *
 * <p>Те же ограничения и CAS-переходы, что и в H2-тестах, проверяются на боевой
 * СУБД: уникальный индекс с несколькими NULL и условный UPDATE должны работать
 * одинаково. Требуется работающий Docker.
 */
@DataR2dbcTest
@ImportAutoConfiguration(LiquibaseAutoConfiguration.class)
@Import({LiquibaseDataSourceConfig.class, PostgreSqlTestContainerConfiguration.class})
@ActiveProfiles("testcontainers")
class CheckoutAttemptRepositoryPostgreSqlTest extends DatabaseResetTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    private CheckoutAttemptRepository checkoutAttemptRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void createUsers() {
        databaseClient
                .sql("INSERT INTO users (username, password_hash, enabled, created_at) "
                        + "VALUES ('user1', 'hash1', TRUE, CURRENT_TIMESTAMP)")
                .then()
                .block();
        databaseClient
                .sql("INSERT INTO users (username, password_hash, enabled, created_at) "
                        + "VALUES ('user2', 'hash2', TRUE, CURRENT_TIMESTAMP)")
                .then()
                .block();
    }

    @Override
    protected List<String> resetStatements() {
        return postgreSqlResetStatements();
    }

    @Test
    void runsOnRealPostgreSqlNotOnH2() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
    }

    @Test
    void terminalAttemptsWithNullActiveUserCoexistOnRealPostgreSql() {
        CheckoutAttempt first = new CheckoutAttempt("attempt-1", USER_ID);
        first.setActiveUserId(null);
        first.setStatus(CheckoutAttempt.Status.PAID.name());
        CheckoutAttempt second = new CheckoutAttempt("attempt-2", OTHER_USER_ID);
        second.setActiveUserId(null);
        second.setStatus(CheckoutAttempt.Status.PAYMENT_TIMEOUT.name());

        assertThat(checkoutAttemptRepository.save(first).block()).isNotNull();
        assertThat(checkoutAttemptRepository.save(second).block()).isNotNull();
        assertThat(checkoutAttemptRepository.count().block()).isEqualTo(2L);
    }

    @Test
    void secondActiveAttemptForSameUserIsRejectedOnRealPostgreSql() {
        checkoutAttemptRepository
                .save(new CheckoutAttempt("attempt-1", USER_ID))
                .block();

        CheckoutAttempt second = new CheckoutAttempt("attempt-2", USER_ID);

        assertThatThrownBy(() -> checkoutAttemptRepository.save(second).block())
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(checkoutAttemptRepository.findByActiveUserId(USER_ID).block())
                .isNotNull()
                .extracting(CheckoutAttempt::getAttemptId)
                .isEqualTo("attempt-1");
    }

    @Test
    void transitionStatusHonorsCasOnRealPostgreSql() {
        CheckoutAttempt saved = checkoutAttemptRepository
                .save(new CheckoutAttempt("attempt-1", USER_ID))
                .block();

        Integer updated = checkoutAttemptRepository
                .transitionStatus(
                        saved.getId(),
                        CheckoutAttempt.Status.PAID.name(),
                        null,
                        42L,
                        3000L,
                        7000L,
                        CheckoutAttempt.Status.AWAITING_PAYMENT.name())
                .block();

        assertThat(updated).isEqualTo(1);
        Integer staleUpdate = checkoutAttemptRepository
                .transitionStatus(
                        saved.getId(),
                        CheckoutAttempt.Status.PAYMENT_TIMEOUT.name(),
                        USER_ID,
                        null,
                        null,
                        null,
                        CheckoutAttempt.Status.AWAITING_PAYMENT.name())
                .block();

        assertThat(staleUpdate).isZero();
        CheckoutAttempt unchanged =
                checkoutAttemptRepository.findById(saved.getId()).block();
        assertThat(unchanged).isNotNull();
        assertThat(unchanged.getStatus()).isEqualTo(CheckoutAttempt.Status.PAID.name());
        assertThat(unchanged.getOrderId()).isEqualTo(42L);
        assertThat(unchanged.getPaymentAmount()).isEqualTo(3000L);
        assertThat(unchanged.getPaymentBalance()).isEqualTo(7000L);
    }
}
