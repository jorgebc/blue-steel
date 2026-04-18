package com.bluesteel.adapters.in.web.health;

import com.bluesteel.application.port.out.ComponentStatus;
import com.bluesteel.application.port.out.OverallStatus;

public record HealthResponse(OverallStatus status, ComponentStatus db) {}
