# syntax=docker/dockerfile:1

# ---- build: full JDK + Maven, thrown away after the jar is produced ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Dependencies first, so editing source does not re-download them (this layer is cached until the pom changes).
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Tests need Docker (Testcontainers), so they run in CI, not inside the image build.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && java -Djarmode=layertools -jar target/sales-tracker-*.jar extract --destination /build/layers

# ---- run: JRE only ----
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
# Layers ordered least- to most-frequently changed, so a code-only rebuild reuses the big dependency layers.
COPY --from=build /build/layers/dependencies/ ./
COPY --from=build /build/layers/spring-boot-loader/ ./
COPY --from=build /build/layers/snapshot-dependencies/ ./
COPY --from=build /build/layers/application/ ./
USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
