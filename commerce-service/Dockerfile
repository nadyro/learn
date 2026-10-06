# syntax=docker/dockerfile:1

# =====================================================================================================================
# Build stage: compiles the application with the exact Maven version of the wrapper.
# =====================================================================================================================
FROM eclipse-temurin:24-jdk-noble AS build
WORKDIR /workspace

# Copy the build definition alone first. Dependencies are downloaded in their own layer, which Docker reuses until
# pom.xml changes: editing a Java file does not re-download the internet.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
# Tests are not run here on purpose: CI runs the full suite (which needs Docker for Testcontainers) before this image
# is built. Formatting and coverage checks are CI concerns too.
# The fat jar is then split into layers: dependencies change rarely, our code changes often, so only the small
# application layer is rebuilt, pushed and pulled on each deployment.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress package \
        -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true \
 && cp target/commerce-service-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# =====================================================================================================================
# Runtime stage: only a JRE and the application, no build tools or source code.
# =====================================================================================================================
FROM eclipse-temurin:24-jre-noble AS runtime

# Never run as root: if the application is compromised, the attacker is an unprivileged user in the container.
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER 10001:10001

# 8080: API. 8081: actuator (health probes, metrics), must not be exposed outside the cluster.
EXPOSE 8080 8081

# - MaxRAMPercentage: size the heap from the container memory limit instead of the host's memory.
# - ExitOnOutOfMemoryError: crash and let the orchestrator restart us rather than limp along in a broken state.
# - LOG_FORMAT=ecs: JSON logs for the log platform (see application.yml).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    LOG_FORMAT=ecs

# No HEALTHCHECK: the orchestrator probes /actuator/health/liveness and /actuator/health/readiness on port 8081.
ENTRYPOINT ["java", "-jar", "application.jar"]
