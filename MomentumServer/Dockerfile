# syntax=docker/dockerfile:1.4
FROM gradle:8.7-jdk21 AS build

WORKDIR /app

COPY gradle ./gradle
COPY gradlew ./gradlew
COPY gradlew.bat ./gradlew.bat
COPY build.gradle.kts settings.gradle.kts gradle.properties ./

RUN sed -i 's/\r$//' gradlew
RUN chmod +x gradlew

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew build --no-daemon -x test

FROM eclipse-temurin:21-jre

WORKDIR /app

ADD https://gu-st.ru/content/Other/doc/russiantrustedca.pem /tmp/russiantrustedca.pem

RUN awk 'BEGIN { n = 0 } /-----BEGIN CERTIFICATE-----/ { n++ } n > 0 { print > ("/tmp/russiantrustedca-" n ".crt") }' /tmp/russiantrustedca.pem \
    && for cert in /tmp/russiantrustedca-*.crt; do \
        keytool -importcert -noprompt -trustcacerts \
            -alias "russiantrustedca-$(basename "$cert" .crt)" \
            -file "$cert" \
            -keystore "$JAVA_HOME/lib/security/cacerts" \
            -storepass changeit; \
    done \
    && rm /tmp/russiantrustedca.pem /tmp/russiantrustedca-*.crt

COPY --from=build /app/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
