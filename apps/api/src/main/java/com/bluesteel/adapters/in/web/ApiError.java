package com.bluesteel.adapters.in.web;

/**
 * Single error element within the response envelope. {@code code} is machine-readable; {@code
 * field} is non-null only for field-level validation errors (ERR-01).
 */
public record ApiError(String code, String message, String field) {

  public static ApiError of(String code, String message) {
    return new ApiError(code, message, null);
  }

  public static ApiError ofField(String code, String message, String field) {
    return new ApiError(code, message, field);
  }
}
