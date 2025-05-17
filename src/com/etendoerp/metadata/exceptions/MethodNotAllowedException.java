package com.etendoerp.metadata.exceptions;

public class MethodNotAllowedException extends RuntimeException {
  private final static String DEFAULT_MESSAGE = "Method not allowed";

  public MethodNotAllowedException(String message) {
    super(message.isEmpty() ? DEFAULT_MESSAGE : message);
  }

  public MethodNotAllowedException() {
    super(DEFAULT_MESSAGE);
  }
}
