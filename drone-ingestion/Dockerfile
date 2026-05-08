# ── Stage 1: Build ─────────────────────────────────────────────────────────────
# Full JDK + Maven to compile and package the fat JAR.
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy POM first so dependency downloads are cached as a separate layer.
# Re-downloaded only when pom.xml changes, not on every source change.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build (tests skipped — CI runs them separately)
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ───────────────────────────────────────────────────────────
# Minimal JRE-only Alpine image — ~100 MB vs ~500 MB for the full JDK image.
FROM eclipse-temurin:17-jre-alpine

# Non-root user for security
RUN addgroup -S drone && adduser -S drone -G drone

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=build /app/target/drone-analytics.jar app.jar

# Persistent volume mount point for H2 file DB (dev) or temp uploads
RUN mkdir -p /app/data && chown drone:drone /app/data

USER drone

EXPOSE 8080

# Spring profile is set via SPRING_PROFILES_ACTIVE env var in docker-compose
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
