# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-22 AS build

WORKDIR /app
COPY . .

RUN --mount=type=secret,id=maven_settings \
    --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests \
    -s /run/secrets/maven_settings

# ---- Run stage ----
FROM eclipse-temurin:22-jdk

ARG APP_NAME=bataillecorse-backend
ARG VERSION=1.0-SNAPSHOT

RUN useradd -m appuser
USER appuser

WORKDIR /app
COPY --from=build /app/target/${APP_NAME}-${VERSION}.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]