package com.bluesteel.adapters.in.web;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
    List<ApiError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                (FieldError fe) ->
                    ApiError.ofField("VALIDATION_ERROR", fe.getDefaultMessage(), fe.getField()))
            .toList();
    return ApiResponse.error(errors);
  }

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiResponse<Void> handleAccessDenied(AccessDeniedException ex) {
    return ApiResponse.error(ApiError.of("ACCESS_DENIED", "Access denied"));
  }

  @ExceptionHandler(RuntimeException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<Void> handleUnexpected(RuntimeException ex) {
    log.error("Unhandled exception", ex);
    return ApiResponse.error(ApiError.of("INTERNAL_ERROR", "An unexpected error occurred"));
  }
}
