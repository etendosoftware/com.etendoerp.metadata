package com.etendoerp.metadata.exceptions;

public class MethodNotAllowedException extends RuntimeException {
    public MethodNotAllowedException(String message) {
        super(message);
    }

    public MethodNotAllowedException() {
        super();
    }
}
