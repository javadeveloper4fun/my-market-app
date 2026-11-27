package ru.yandex.practicum.mymarket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.mymarket.config.LiquibaseDataSourceConfig;
import ru.yandex.practicum.mymarket.model.CheckoutAttempt;
import ru.yandex.practicum.mymarket.support.DatabaseResetTest;

/**
 * Тесты репозитория попыток оформления заказа на встроенной H2 (профиль test).
 *
 * <p>Проверяются три инварианта: несколько терминальных попыток с пустым
 * {@code active_user_id} допустимы, вторая активная попытка того же пользователя
 * отклоняется уникальным индексом, а смена статуса через CAS не стирает данные,
 * если состояние уже изменилось.
 */
@DataR2dbcTest
@ImportAutoConfiguration(LiquibaseAutoConfiguration.class)
@Import(LiquibaseDataSourceConfig.class)
@ActiveProfiles("test")
class CheckoutAttemptRepositoryTest extends DatabaseResetTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    private CheckoutAttemptRepository checkoutAttemptRepository;

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

    @Test
    void terminalAttemptsWithNullActiveUserCoexist() {
        CheckoutAttempt first = new CheckoutAttempt("attempt-1", USER_ID);
        first.setActiveUserId(null);
        first.setStatus(CheckoutAttempt.Status.PAID.name());
        CheckoutAttempt second = new CheckoutAttempt("attempt-2", OTHER_USER_ID);
        second.setActiveUserId(null);
        second.setStatus(CheckoutAttempt.Status.INSUFFICIENT_FUNDS.name());

        assertThat(checkoutAttemptRepository.save(first).block()).isNotNull();
        assertThat(checkoutAttemptRepository.save(second).block()).isNotNull();

        assertThat(checkoutAttemptRepository.count().block()).isEqualTo(2L);
    }

    @Test
    void secondActiveAttemptForSameUserIsRejected() {
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
    void transitionStatusAppliesPayloadOnlyForExpectedStatus() {
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
        CheckoutAttempt paid = checkoutAttemptRepository.findById(saved.getId()).block();
        assertThat(paid).isNotNull();
        assertThat(paid.getStatus()).isEqualTo(CheckoutAttempt.Status.PAID.name());
        assertThat(paid.getActiveUserId()).isNull();
        assertThat(paid.getOrderId()).isEqualTo(42L);
        assertThat(paid.getPaymentAmount()).isEqualTo(3000L);
        assertThat(paid.getPaymentBalance()).isEqualTo(7000L);

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
        assertThat(unchanged.getActiveUserId()).isNull();
        assertThat(unchanged.getOrderId()).isEqualTo(42L);
        assertThat(unchanged.getPaymentAmount()).isEqualTo(3000L);
        assertThat(unchanged.getPaymentBalance()).isEqualTo(7000L);
    }

    @Test
    void attachOrderIfNullIsIdempotent() {
        CheckoutAttempt saved = checkoutAttemptRepository
                .save(new CheckoutAttempt("attempt-1", USER_ID))
                .block();

        assertThat(checkoutAttemptRepository
                        .attachOrderIfNull(saved.getId(), 7L)
                        .block())
                .isEqualTo(1);
        assertThat(checkoutAttemptRepository
                        .attachOrderIfNull(saved.getId(), 8L)
                        .block())
                .isZero();

        assertThat(checkoutAttemptRepository.findById(saved.getId()).block())
                .isNotNull()
                .extracting(CheckoutAttempt::getOrderId)
                .isEqualTo(7L);
    }

    @Test
    void findsAttemptByIdAndActiveUser() {
        CheckoutAttempt saved = checkoutAttemptRepository
                .save(new CheckoutAttempt("attempt-1", USER_ID))
                .block();

        assertThat(checkoutAttemptRepository.findByAttemptId("attempt-1").block())
                .isNotNull()
                .extracting(CheckoutAttempt::getId)
                .isEqualTo(saved.getId());
        assertThat(checkoutAttemptRepository.findByAttemptId("missing").block()).isNull();
    }
}
