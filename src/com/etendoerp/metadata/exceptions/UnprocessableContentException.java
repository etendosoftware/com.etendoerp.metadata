package com.etendoerp.metadata.exceptions;

public class UnprocessableContentException extends RuntimeException {
    public UnprocessableContentException() {
        super("Unprocessable content");
    }

    public UnprocessableContentException(String message) {
        super(message);
    }

    public UnprocessableContentException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnprocessableContentException(Throwable cause) {
        super(cause);
    }
}