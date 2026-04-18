package com.bluesteel.adapters.in.web;

public record ApiError(String code, String message, String field) {

  public static ApiError of(String code, String message) {
    return new ApiError(code, message, null);
  }

  public static ApiError ofField(String code, String message, String field) {
    return new ApiError(code, message, field);
  }
}
