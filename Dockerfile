# Multi-Stage Build: Spring-Boot 2.6.6 + Java 21 + WAR + embedded Angular (frontend-maven-plugin)
# Build-Stage: Maven + JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy full repo (ng-build and api-build share the same source tree)
COPY . .

# Build: -pl climatedataanalyser-api -am also builds the ng module first via frontend-maven-plugin,
# which downloads node/npm itself (requires network access in CI — expected).
RUN mvn -B -ntp -DskipTests -pl climatedataanalyser-api -am package

# Runtime-Stage: slim JRE 21
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as non-root user (useradd must come before COPY so --chown works)
RUN useradd --system --no-create-home --shell /sbin/nologin appuser

# warName=ClimateAnalyser produces ClimateAnalyser.war (plain, no Main-Class).
# Spring Boot repackages to climatedataanalyser-api-<version>.war (executable, WarLauncher).
# Glob matches only the repackaged WAR; ClimateAnalyser.war does NOT match this pattern.
COPY --chown=appuser:appuser --from=build /build/climatedataanalyser-api/target/climatedataanalyser-api-*.war /app.war

USER appuser

# server.port=8092 (verified in climatedataanalyser-api/src/main/resources/application.properties)
EXPOSE 8092

# DB credentials must be supplied at runtime via env vars, e.g.:
#   -e SPRING_DATASOURCE_URL=jdbc:mysql://...
#   -e SPRING_DATASOURCE_USERNAME=...
#   -e SPRING_DATASOURCE_PASSWORD=...
ENTRYPOINT ["java", "-jar", "/app.war"]
