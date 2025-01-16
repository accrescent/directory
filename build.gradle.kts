// Copyright 2024-2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.quarkus)
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(libs.quarkus.hibernate.reactive)
    implementation(libs.quarkus.hibernate.reactive.panache)
    implementation(libs.quarkus.kotlin)
    implementation(libs.quarkus.arc)
    implementation(libs.quarkus.grpc)
    implementation(libs.quarkus.reactive.pg)
    testImplementation(libs.quarkus.junit5)
}

group = "app.accrescent.services"
version = "0.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        javaParameters = true
    }
}

dokka {
    dokkaPublications.configureEach {
        failOnWarning = true
    }

    dokkaSourceSets.configureEach {
        reportUndocumented = true

        perPackageOption {
            matchingRegex = """^(app\.accrescent\.directory\.(internal\.)?v1|com\.(android\.bundle|google\.protobuf))"""
            suppress = true
        }
    }
}
