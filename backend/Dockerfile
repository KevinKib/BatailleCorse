# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-22 AS build

WORKDIR /app

# Dependencies live in an image layer keyed on pom.xml, not in a BuildKit cache mount: Docker
# Desktop's builder garbage-collects cache mounts past 2.7 GiB (shared with every build context),
# which emptied the Maven repository between two builds. A code change now reuses this layer and
# only compiles. The repository path is explicit so no VOLUME of the base image can drop it.
ENV MAVEN_REPO=/opt/maven-repo
COPY pom.xml .
RUN --mount=type=secret,id=maven_settings \
    mvn -B -q dependency:go-offline \
    -Dmaven.repo.local=$MAVEN_REPO -s /run/secrets/maven_settings

COPY src ./src
# Tests are neither compiled nor run here: the test suites run outside the image (mvn test, CI).
RUN --mount=type=secret,id=maven_settings \
    mvn -B clean package -Dmaven.test.skip=true \
    -Dmaven.repo.local=$MAVEN_REPO -s /run/secrets/maven_settings

# ---- Dev stage ----
FROM maven:3.9-eclipse-temurin-22 AS dev

WORKDIR /app

RUN apt-get update && apt-get install -y inotify-tools && rm -rf /var/lib/apt/lists/*

COPY entrypoint-dev.sh /entrypoint-dev.sh
# Strip Windows CRLF line endings in case the file was saved on Windows
RUN sed -i 's/\r$//' /entrypoint-dev.sh && chmod +x /entrypoint-dev.sh

ENTRYPOINT ["/entrypoint-dev.sh"]

# ---- Run stage ----
# Last stage, so the default target of the production build. The e2e stack (docker-compose.e2e.yml)
# selects it by name (`target: run`) to start a prebuilt jar instead of compiling in the container.
FROM eclipse-temurin:22-jdk AS run

ARG APP_NAME=bataillecorse-backend
ARG VERSION=1.0-SNAPSHOT

RUN useradd -m appuser
USER appuser

WORKDIR /app
COPY --from=build /app/target/${APP_NAME}-${VERSION}.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]