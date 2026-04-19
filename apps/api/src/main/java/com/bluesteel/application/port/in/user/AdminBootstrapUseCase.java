package com.bluesteel.application.port.in.user;

/** Seeds the singleton admin user if none exists, and recovers any stuck sessions. */
public interface AdminBootstrapUseCase {

  void bootstrap();
}
