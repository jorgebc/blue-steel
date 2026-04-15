# Blue Steel API — production Docker image
# Target platform: linux/arm64 (Oracle Cloud Always Free ARM VM — D-046)
# Built by CI via: docker buildx build --platform linux/arm64 ...
#
# Multi-stage build: build stage produces the JAR; runtime stage is minimal.
# The JAR is built by Maven in CI (mvn package -DskipTests) before Docker build.

# ── Runtime stage ──────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

# Copy the JAR produced by mvn package
COPY target/blue-steel-api-*.jar app.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Run as a non-root user
RUN groupadd --system --gid 1001 appgroup \
    && useradd --system --uid 1001 --gid appgroup appuser
USER appuser

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
