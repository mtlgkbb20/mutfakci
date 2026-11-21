package com.kitchenai.common;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  static final String UNEXPECTED = "Unexpected server error. Please contact support.";
  static final String INVALID_REQUEST = "Invalid request";

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException exception) {
    String errorId = UUID.randomUUID().toString();
    log.warn(
        "API error id={} status={} message={}",
        errorId,
        exception.status.value(),
        exception.getMessage(),
        exception);
    return body(exception.status, exception.getMessage(), errorId, null);
  }

  /** Bean-validation failures on {@code @Valid @RequestBody}: 400 with the offending field names. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> invalidBody(MethodArgumentNotValidException exception) {
    String errorId = UUID.randomUUID().toString();
    List<String> fields =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField())
            .distinct()
            .sorted()
            .toList();
    log.warn("Validation error id={} fields={}", errorId, fields);
    return body(HttpStatusCode.valueOf(400), INVALID_REQUEST, errorId, fields);
  }

  /** Malformed JSON, wrong parameter types and constraint violations are client errors. */
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    TypeMismatchException.class,
    ConstraintViolationException.class
  })
  ResponseEntity<?> badRequest(Exception exception) {
    String errorId = UUID.randomUUID().toString();
    log.warn("Bad request id={} type={}", errorId, exception.getClass().getSimpleName());
    return body(HttpStatusCode.valueOf(400), INVALID_REQUEST, errorId, null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> generic(Exception exception) {
    String errorId = UUID.randomUUID().toString();
    // ResponseStatusException and Spring MVC exceptions (missing parameter, 405, 404, 413, ...)
    // already carry the right status; only genuinely unexpected failures become a masked 500.
    if (exception instanceof ErrorResponse errorResponse
        && !errorResponse.getStatusCode().is5xxServerError()) {
      HttpStatusCode status = errorResponse.getStatusCode();
      log.warn(
          "Client error id={} status={} type={} message={}",
          errorId,
          status.value(),
          exception.getClass().getSimpleName(),
          exception.getMessage());
      return body(status, clientMessage(exception, status), errorId, null);
    }
    log.error(
        "Unhandled API error id={} type={} message={}",
        errorId,
        exception.getClass().getName(),
        exception.getMessage(),
        exception);
    return body(HttpStatusCode.valueOf(500), UNEXPECTED, errorId, null);
  }

  private static String clientMessage(Exception exception, HttpStatusCode status) {
    if (exception instanceof ResponseStatusException rse && rse.getReason() != null) {
      return rse.getReason();
    }
    return status.value() == 400 ? INVALID_REQUEST : defaultMessage(status);
  }

  private static String defaultMessage(HttpStatusCode status) {
    var known = org.springframework.http.HttpStatus.resolve(status.value());
    return known == null ? "Request failed" : known.getReasonPhrase();
  }

  private static ResponseEntity<?> body(
      HttpStatusCode status, String message, String errorId, List<String> fields) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now());
    body.put("error", message);
    body.put("errorId", errorId);
    if (fields != null) body.put("fields", fields);
    return ResponseEntity.status(status).body(body);
  }
}
