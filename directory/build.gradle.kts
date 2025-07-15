// Copyright 2024 Logan Magee
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

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(libs.bundletool)
    implementation(libs.flyway.postgresql)
    implementation(libs.protobuf.kotlin)
    implementation(libs.quarkus.agroal)
    implementation(libs.quarkus.flyway)
    implementation(libs.quarkus.hibernate.reactive)
    implementation(libs.quarkus.hibernate.reactive.panache)
    implementation(libs.quarkus.hibernate.reactive.panache.kotlin)
    implementation(libs.quarkus.jdbc.postgresql)
    implementation(libs.quarkus.kotlin)
    implementation(libs.quarkus.messaging.kafka)
    implementation(libs.quarkus.arc)
    implementation(libs.quarkus.grpc)
    implementation(libs.quarkus.reactive.pg)
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.quarkus.test.hibernate.reactive.panache)
    testImplementation(libs.quarkus.test.vertx)
}

group = "app.accrescent.services"
version = "0.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        nativeImageCapable = true
    }

    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        javaParameters = true
    }
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

dokka {
    dokkaPublications.configureEach {
        failOnWarning = true
    }

    dokkaSourceSets.configureEach {
        reportUndocumented = true

        perPackageOption {
            matchingRegex =
                """^(app\.accrescent\.(com\.android\.bundle|directory\.(priv\.|push\.)?v1(beta1)?|events\.v1)|com\.google\.protobuf)"""
            suppress = true
        }
    }
}
