package com.etendoerp.metadata.exceptions;

public class InternalServerException extends RuntimeException {
    public InternalServerException(String message) {
        super(message);
    }

    public InternalServerException() {
        super();
    }
}
