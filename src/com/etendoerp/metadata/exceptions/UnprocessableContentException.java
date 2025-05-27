package com.etendoerp.metadata.exceptions;

public class UnprocessableContentException extends RuntimeException {
    private final static String DEFAULT_MESSAGE = "Unprocessable content";

    public UnprocessableContentException(String message) {
        super(message.isEmpty() ? DEFAULT_MESSAGE : message);
    }

    public UnprocessableContentException() {
        super(DEFAULT_MESSAGE);
    }
}
