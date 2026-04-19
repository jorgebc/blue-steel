package com.bluesteel.adapters.in.web;

import com.bluesteel.domain.exception.InvalidCredentialsException;
import com.bluesteel.domain.exception.InvalidPasswordException;
import com.bluesteel.domain.exception.RefreshTokenException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.exception.UserNotFoundException;
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

/**
 * Maps exceptions to the standard error envelope ({@code { "errors": [...] }}) defined in ERR-01.
 * Never leaks stack traces to the client.
 */
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

  @ExceptionHandler(UnauthorizedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiResponse<Void> handleUnauthorized(UnauthorizedException ex) {
    return ApiResponse.error(ApiError.of("FORBIDDEN", ex.getMessage()));
  }

  @ExceptionHandler(UserNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> handleUserNotFound(UserNotFoundException ex) {
    return ApiResponse.error(ApiError.of("USER_NOT_FOUND", ex.getMessage()));
  }

  @ExceptionHandler(InvalidPasswordException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  public ApiResponse<Void> handleInvalidPassword(InvalidPasswordException ex) {
    return ApiResponse.error(ApiError.of("INVALID_CURRENT_PASSWORD", ex.getMessage()));
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiResponse<Void> handleInvalidCredentials(InvalidCredentialsException ex) {
    return ApiResponse.error(ApiError.of("INVALID_CREDENTIALS", ex.getMessage()));
  }

  @ExceptionHandler(RefreshTokenException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiResponse<Void> handleRefreshToken(RefreshTokenException ex) {
    return ApiResponse.error(ApiError.of(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(RuntimeException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<Void> handleUnexpected(RuntimeException ex) {
    log.error("Unhandled exception", ex);
    return ApiResponse.error(ApiError.of("INTERNAL_ERROR", "An unexpected error occurred"));
  }
}
