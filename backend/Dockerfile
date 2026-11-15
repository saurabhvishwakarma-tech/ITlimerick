# ---- Build stage: compiles the Spring Boot jar (needs internet access to Maven Central) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:resolve dependency:resolve-plugins
COPY src ./src
# Tests need a PostgreSQL database, so they run in CI (see .github/workflows/ci.yml), not here.
RUN mvn -B -q -DskipTests package

# ---- Runtime stage: small JRE image, runs as a non-root user ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/target/summit-api-*.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
