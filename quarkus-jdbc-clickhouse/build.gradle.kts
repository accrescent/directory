// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.quarkus.extension)
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(24)
        nativeImageCapable = true
    }

    compilerOptions {
        jvmTarget = JvmTarget.JVM_24
    }

    explicitApi()
}

quarkusExtension {
    deploymentModule = "quarkus-jdbc-clickhouse-deployment"
}

dependencies {
    implementation(platform(libs.quarkus.bom))
    implementation(libs.brotli)
    implementation(libs.clickhouse.jdbc)
    implementation(libs.quarkus.agroal)
    implementation(libs.xz)
    implementation(libs.zstd)
}
