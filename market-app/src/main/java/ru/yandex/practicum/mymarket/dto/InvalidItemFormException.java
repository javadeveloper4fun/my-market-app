package ru.yandex.practicum.mymarket.dto;

/**
 * Поля формы добавления товара заполнены неверно.
 *
 * <p>Отдельный тип нужен, чтобы отказ в пользовательском вводе не выглядел как
 * сбой приложения. Проверки формы раньше не было вовсе, и незаполненная цена
 * приводила к {@link NullPointerException} при распаковке {@link Long} в
 * примитив: пользователь получал 500 на вводе, который должен отклоняться
 * кодом 400.
 *
 * <p>Сообщение показывается пользователю, поэтому оно пишется как причина на
 * человеческом языке, а не как имя violated-поля.
 */
public class InvalidItemFormException extends RuntimeException {

    /**
     * @param message причина, показываемая пользователю
     */
    public InvalidItemFormException(String message) {
        super(message);
    }
}
