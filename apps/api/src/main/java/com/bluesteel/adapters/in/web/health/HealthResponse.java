package com.bluesteel.adapters.in.web.health;

import com.bluesteel.application.model.health.ComponentStatus;
import com.bluesteel.application.model.health.OverallStatus;

public record HealthResponse(OverallStatus status, ComponentStatus db) {}
