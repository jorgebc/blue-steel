# Blue Steel API — production Docker image
# Target platform: linux/amd64 (Render free tier web service — D-091)
# Built by CI via: docker buildx build --platform linux/amd64 ...
#
# Multi-stage build: build stage produces the JAR; runtime stage is minimal.
# The JAR is built by Maven in CI (mvn package -DskipTests) before Docker build.

# ── Runtime stage ──────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-jammy@sha256:d36843a6f1af5d0aca01ef3d926e2220444eb19fe38e1b23cef3d663ef29b306

WORKDIR /app

# Copy the JAR produced by mvn package
COPY target/blue-steel-api-*.jar app.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Run as a non-root user
RUN groupadd --system --gid 1001 appgroup \
    && useradd --system --uid 1001 --gid appgroup appuser
USER appuser

# JVM tuning for Render free tier (512 MB total RAM).
# SerialGC: lowest-overhead collector for a single-core, low-memory container
# (the JVM already auto-selects it under <2 CPUs / <1792 MB — set explicitly for clarity).
# Budget: ~256 heap + ~128 metaspace cap + code cache + threads + native ≈ <460 MB,
# leaving headroom under the 512 MB ceiling. Overridable from the Render dashboard.
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
