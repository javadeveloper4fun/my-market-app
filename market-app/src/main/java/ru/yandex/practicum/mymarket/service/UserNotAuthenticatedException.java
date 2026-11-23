package ru.yandex.practicum.mymarket.service;

public class UserNotAuthenticatedException extends RuntimeException {

    public UserNotAuthenticatedException() {
        super("User is not authenticated");
    }
}
