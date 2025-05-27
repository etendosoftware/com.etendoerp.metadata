package com.etendoerp.metadata.exceptions;

public class NotFoundException extends RuntimeException {
    private final static String DEFAULT_MESSAGE = "Not found";

    public NotFoundException(String message) {
        super(message.isEmpty() ? DEFAULT_MESSAGE : message);
    }

    public NotFoundException() {
        super(DEFAULT_MESSAGE);
    }
}
