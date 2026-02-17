FROM gradle:8.7-jdk21 AS build
WORKDIR /app

COPY MomentumServer/gradle /app/gradle
COPY MomentumServer/gradlew /app/gradlew
COPY MomentumServer/gradlew.bat /app/gradlew.bat
COPY MomentumServer/build.gradle.kts /app/build.gradle.kts
COPY MomentumServer/settings.gradle.kts /app/settings.gradle.kts
COPY MomentumServer/gradle.properties /app/gradle.properties
COPY MomentumServer/src /app/src

RUN chmod +x /app/gradlew
RUN /app/gradlew buildFatJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*-all.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
