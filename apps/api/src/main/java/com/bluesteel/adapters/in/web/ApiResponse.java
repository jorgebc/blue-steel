package com.bluesteel.adapters.in.web;

import java.util.List;
import java.util.Map;

public record ApiResponse<T>(T data, Object meta, List<ApiError> errors) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(data, Map.of(), List.of());
  }

  public static ApiResponse<Void> error(List<ApiError> errors) {
    return new ApiResponse<>(null, Map.of(), errors);
  }

  public static ApiResponse<Void> error(ApiError error) {
    return error(List.of(error));
  }
}
