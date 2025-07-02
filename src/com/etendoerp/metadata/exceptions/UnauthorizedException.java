package com.etendoerp.metadata.exceptions;

public class UnauthorizedException extends RuntimeException {
    private final static String DEFAULT_MESSAGE = "Invalid or missing token";

    public UnauthorizedException(String message) {
        super(message.isEmpty() ? DEFAULT_MESSAGE : message);
    }

    public UnauthorizedException() {
        super(DEFAULT_MESSAGE);
    }
}
