package ru.yandex.practicum.payments.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.yandex.practicum.payments.model.ErrorResponse;
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

    @ExceptionHandler(InvalidAmountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAmount(InvalidAmountException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_AMOUNT", exception.getMessage()));
    }
}
