// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.quarkus.extension)
}

kotlin {
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
