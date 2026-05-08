package com.platform.api.exception;

public class IngestionQueueException extends RuntimeException {

  public IngestionQueueException(String message, Throwable cause) {
    super(message, cause);
  }
}
