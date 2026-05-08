val ktorVersion: String = "3.4.0"
val kotlinVersion: String = "2.3.0"
val logbackVersion: String = "1.4.14"
val exposedVersion: String = "0.56.0"

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
    alias(libs.plugins.ktor)
}

group = "com.momentum"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.openapi)
    implementation(libs.ktor.server.routing.openapi)
    implementation(libs.ktor.server.metrics)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)
    implementation("org.mindrot:jbcrypt:0.4")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.postgresql:postgresql:42.7.10")

    implementation("org.flywaydb:flyway-core:10.17.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.17.0")

    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation(libs.minio)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)

    implementation("jakarta.mail:jakarta.mail-api:2.1.1")
    implementation("org.eclipse.angus:angus-mail:2.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")


    implementation("com.google.firebase:firebase-admin:9.8.0")
}
