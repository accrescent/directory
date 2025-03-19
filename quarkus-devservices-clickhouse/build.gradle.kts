// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
}

dependencies {
    implementation(platform(libs.quarkus.bom))
    implementation(libs.quarkus.datasource.deployment.spi)
    implementation(libs.quarkus.devservices.common)
    implementation(libs.testcontainers.clickhouse)
    kapt(libs.quarkus.extension.processor)
}
