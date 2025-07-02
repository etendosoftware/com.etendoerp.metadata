package com.etendoerp.metadata.exceptions;

public class InternalServerException extends RuntimeException {
    private final static String DEFAULT_MESSAGE = "Internal server error";

    public InternalServerException(String message) {
        super(message.isEmpty() ? DEFAULT_MESSAGE : message);
    }

    public InternalServerException() {
        super(DEFAULT_MESSAGE);
    }
}
