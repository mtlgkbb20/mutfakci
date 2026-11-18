package com.kitchenai.common;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException exception) {
    String errorId = UUID.randomUUID().toString();
    log.warn(
        "API error id={} status={} message={}",
        errorId,
        exception.status.value(),
        exception.getMessage(),
        exception);
    return ResponseEntity.status(exception.status)
        .body(
            Map.of(
                "timestamp",
                Instant.now(),
                "error",
                exception.getMessage(),
                "errorId",
                errorId));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> generic(Exception exception) {
    String errorId = UUID.randomUUID().toString();
    log.error(
        "Unhandled API error id={} type={} message={}",
        errorId,
        exception.getClass().getName(),
        exception.getMessage(),
        exception);
    return ResponseEntity.internalServerError()
        .body(
            Map.of(
                "timestamp",
                Instant.now(),
                "error",
                "Unexpected server error. Please contact support.",
                "errorId",
                errorId));
  }
}
