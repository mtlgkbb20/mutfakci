package com.kitchenai.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  public final HttpStatus status;

  public ApiException(HttpStatus s, String m) {
    super(m);
    status = s;
  }
}
