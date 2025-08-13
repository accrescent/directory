// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(24)
        nativeImageCapable = true
    }

    compilerOptions {
        jvmTarget = JvmTarget.JVM_24
    }
}

dependencies {
    implementation(platform(libs.quarkus.bom))
    implementation(libs.quarkus.datasource.deployment.spi)
    implementation(libs.quarkus.devservices.common)
    implementation(libs.testcontainers.clickhouse)
    kapt(libs.quarkus.extension.processor)
}
