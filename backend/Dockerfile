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

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]