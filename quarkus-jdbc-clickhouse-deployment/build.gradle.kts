// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
}

dependencies {
    implementation(platform(libs.quarkus.bom))
    implementation(project(":quarkus-devservices-clickhouse"))
    implementation(project(":quarkus-jdbc-clickhouse"))
    implementation(libs.quarkus.agroal.deployment)
    implementation(libs.quarkus.agroal.spi)
    implementation(libs.quarkus.datasource.deployment.spi)
    implementation(libs.quarkus.devservices.deployment)
    kapt(libs.quarkus.extension.processor)
}
