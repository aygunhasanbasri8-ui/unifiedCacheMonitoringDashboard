# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

# Maven deposu BuildKit cache mount'unda tutulur; sonraki build'ler
# bağımlılıkları yeniden indirmez.
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S ucmd && adduser -S ucmd -G ucmd

COPY --from=build /build/target/ucmd-*.jar app.jar
USER ucmd

EXPOSE 8080

# context-path /api olduğu için actuator da /api altında
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/api/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
