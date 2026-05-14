package com.bluesteel.adapters.in.web.auth;

public record LoginResponse(String accessToken, boolean forcePasswordChange) {}
