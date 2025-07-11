import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        nativeImageCapable = true
    }

    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    implementation(platform(libs.quarkus.bom))
    implementation(libs.quarkus.datasource.deployment.spi)
    implementation(libs.quarkus.devservices.common)
    implementation(libs.testcontainers.clickhouse)
    kapt(libs.quarkus.extension.processor)
}
