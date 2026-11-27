package ru.yandex.practicum.mymarket.service;

/**
 * Ошибка оформления, после которой можно повторить ту же попытку.
 *
 * <p>Несет идентификатор попытки, чтобы повтор пришёл с тем же X-Attempt-Id,
 * и код ошибки для редиректа в корзину.
 */
public class CheckoutRetryException extends RuntimeException {

    private final String attemptId;
    private final String errorCode;

    public CheckoutRetryException(String attemptId, String errorCode, Throwable cause) {
        super(errorCode, cause);
        this.attemptId = attemptId;
        this.errorCode = errorCode;
    }

    public String getAttemptId() {
        return attemptId;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
