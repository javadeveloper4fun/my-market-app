package ru.yandex.practicum.mymarket.service;

/**
 * Картинка товара не загружена или загружена недопустимым образом.
 *
 * <p>Отдельный тип нужен, чтобы отличить собственную ошибку приложения от сбоя
 * хранилища: первая означает «пользователь прислал не то» и разбирается на
 * странице, вторая — «сервис недоступен» и должна попасть в лог как инцидент.
 * Без разделения обе реакции слились бы в один ответ 400, и сбой хранилища
 * проходил бы молча.
 *
 * <p>Наследует {@link RuntimeException}, а не {@code IllegalStateException}
 * намеренно: {@code IllegalStateException} в проекте означает непредвиденную
 * ошибку, которую нельзя показать пользователю как понятную причину.
 */
public class ImageUploadException extends RuntimeException {

    /**
     * @param message причина, показываемая пользователю
     */
    public ImageUploadException(String message) {
        super(message);
    }
}
