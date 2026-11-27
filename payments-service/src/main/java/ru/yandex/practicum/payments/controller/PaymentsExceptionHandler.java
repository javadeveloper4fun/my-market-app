package ru.yandex.practicum.payments.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import ru.yandex.practicum.payments.model.ErrorResponse;
import ru.yandex.practicum.payments.service.AttemptConflictException;
import ru.yandex.practicum.payments.service.InsufficientFundsException;
import ru.yandex.practicum.payments.service.InvalidAmountException;

/**
 * Преобразует прикладные ошибки платежей в ответы, описанные в OpenAPI.
 *
 * <p>Метод создания платежа в сгенерированном интерфейсе возвращает только
 * успешную модель, поэтому бизнес-ошибки передаются исключениями. Такой путь
 * сохраняет единый JSON-формат ошибки и не смешивает успешный и ошибочный
 * контракты в одном типе.
 */
@RestControllerAdvice
public class PaymentsExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException exception) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(new ErrorResponse("INSUFFICIENT_FUNDS", exception.getMessage()));
    }

    @ExceptionHandler(AttemptConflictException.class)
    public ResponseEntity<ErrorResponse> handleAttemptConflict(AttemptConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ATTEMPT_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(InvalidAmountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAmount(InvalidAmountException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_AMOUNT", exception.getMessage()));
    }

    /**
     * Возвращает контрактное тело для ошибки валидации тела запроса.
     *
     * <p>В WebFlux ошибка {@code @Valid} приходит как
     * {@link WebExchangeBindException}, а не как обычное исключение
     * контроллера. Без обработчика её перехватывает стандартный обработчик
     * Spring Boot и отдаёт служебный JSON, не совпадающий с OpenAPI.
     *
     * @param exception ошибка валидации тела
     * @return ответ 400 с телом {@link ErrorResponse}
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(WebExchangeBindException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_AMOUNT", "Сумма платежа должна быть положительной"));
    }

    /**
     * Возвращает контрактное тело для неразобранного или некорректного запроса.
     *
     * <p>{@link WebExchangeBindException} наследуется от
     * {@link ServerWebInputException}, поэтому для ошибок валидации Spring
     * выбирает более конкретный обработчик выше. Этот метод отвечает за битый
     * JSON и другие ошибки чтения тела.
     *
     * @param exception ошибка формата запроса
     * @return ответ 400 с телом {@link ErrorResponse}
     */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(ServerWebInputException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", "Некорректный формат запроса"));
    }
}
