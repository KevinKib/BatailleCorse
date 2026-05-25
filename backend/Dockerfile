# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-22 AS build

WORKDIR /app
COPY . .

RUN --mount=type=secret,id=maven_settings \
    --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests \
    -s /run/secrets/maven_settings

# ---- Dev stage ----
FROM maven:3.9-eclipse-temurin-22 AS dev

WORKDIR /app

RUN apt-get update && apt-get install -y inotify-tools && rm -rf /var/lib/apt/lists/*

COPY entrypoint-dev.sh /entrypoint-dev.sh
# Strip Windows CRLF line endings in case the file was saved on Windows
RUN sed -i 's/\r$//' /entrypoint-dev.sh && chmod +x /entrypoint-dev.sh

ENTRYPOINT ["/entrypoint-dev.sh"]

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