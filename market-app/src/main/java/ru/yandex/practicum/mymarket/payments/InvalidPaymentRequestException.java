package ru.yandex.practicum.mymarket.payments;

/**
 * Сервис платежей отклонил запрос как некорректный.
 *
 * <p>Отдельный тип нужен, чтобы ошибка 400 не выдавалась за недоступность
 * сервиса: причины у этих ответов разные, и пользователю нужно показывать
 * понятное сообщение о сумме, а не «сервис временно недоступен».
 */
public class InvalidPaymentRequestException extends RuntimeException {

    /**
     * @param message причина отказа
     * @param cause   исходная ошибка клиента
     */
    public InvalidPaymentRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
