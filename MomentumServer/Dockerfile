# syntax=docker/dockerfile:1.4
FROM gradle:8.7-jdk21 AS build

WORKDIR /app

COPY gradle ./gradle
COPY gradlew ./gradlew
COPY gradlew.bat ./gradlew.bat
COPY build.gradle.kts settings.gradle.kts gradle.properties ./

RUN chmod +x gradlew

# Mount Gradle Cache(persists between builds)
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew buildFatJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/build/libs/*-all.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]